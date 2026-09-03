package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.transfer.application.TransactionQueryService;
import com.movi_backend.domain.transfer.application.TransferTargetResolver;
import com.movi_backend.domain.transfer.application.TransferValidationService;
import com.movi_backend.domain.transfer.application.BankDirectory;
import com.movi_backend.domain.transfer.application.SpokenAccountNumberParser;
import com.movi_backend.domain.transfer.application.TransferExecutionService;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.voice.application.model.PendingTransferSlots;
import com.movi_backend.domain.voice.application.model.VoiceStreamContext;
import com.movi_backend.domain.voice.application.model.VoiceHistoryPeriod;
import com.movi_backend.domain.voice.client.VoiceAnalysisClient;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntities;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.entity.VoiceCommand;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceCommandRepository;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.domain.voice.type.VoiceSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.util.SensitiveTextMasker;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class VoiceCommandService {

    private static final long MAXIMUM_AUDIO_SIZE = 5L * 1024L * 1024L;
    private static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.80");

    /**
     * 확인 대기 중 취소로 읽는 말. 승인보다 <b>먼저</b> 본다.
     *
     * <p>승인을 먼저 보면 "아니네요"처럼 부정 안에 든 "네"를 승인으로 읽어, 취소하려던
     * 송금이 나간다. 잘못 읽어서 생기는 손해가 한쪽으로만 크므로 취소 쪽으로 기운다.
     */
    private static final List<String> DENIAL_WORDS = List.of(
            "아니", "취소", "그만", "싫", "말아", "마세요", "하지마", "안돼", "안해", "됐어"
    );

    /** 확인 대기 중 승인으로 읽는 말. */
    private static final List<String> APPROVAL_WORDS = List.of(
            "네", "예", "응", "어", "맞아", "맞습니", "맞어", "보내", "해줘", "해주세요",
            "확인", "좋아", "그래", "오케이", "옙", "넵"
    );

    /**
     * 음성 거래내역 조회 1회에 가져올 건수.
     *
     * <p>REST 목록과 달리 페이지를 넘길 수단이 없다. 전부 가져와도 읽어 줄 수 없으므로
     * 최근 몇 건만 확보하고 나머지는 총 건수로 안내한다.
     */
    private static final int HISTORY_PAGE_SIZE = 5;
    private static final List<String> SUPPORTED_AUDIO_TYPES = List.of(
            "audio/webm",
            "audio/mp4",
            "audio/x-m4a",
            "audio/wav",
            "audio/x-wav",
            "audio/wave"
    );

    private final VoiceSessionRepository voiceSessionRepository;
    private final VoiceSessionExpirationService voiceSessionExpirationService;
    private final VoiceCommandRepository voiceCommandRepository;
    private final VoiceAnalysisClient voiceAnalysisClient;
    private final TransferValidationService transferValidationService;
    private final TransferExecutionService transferExecutionService;
    private final TransactionQueryService transactionQueryService;
    private final BalanceInquiryService balanceInquiryService;
    private final TransferTargetResolver transferTargetResolver;
    private final SpokenAccountNumberParser spokenAccountNumberParser;
    private final BankDirectory bankDirectory;
    private final ObjectMapper objectMapper;
    private final AudioDurationValidator audioDurationValidator;

    public VoiceCommandResponse process(
            final Long userId,
            final Long voiceSessionId,
            final MultipartFile audio
    ) {
        return process(userId, voiceSessionId, audio, null, null);
    }

    @Transactional
    public VoiceCommandResponse process(
            final Long userId,
            final Long voiceSessionId,
            final MultipartFile audio,
            final String idempotencyKey
    ) {
        return process(userId, voiceSessionId, audio, null, idempotencyKey);
    }

    @Transactional
    public VoiceCommandResponse process(
            final Long userId,
            final Long voiceSessionId,
            final MultipartFile audio,
            final String confirmationId,
            final String idempotencyKey
    ) {
        validateAudio(audio);
        final LocalDateTime now = LocalDateTime.now();
        final VoiceSession session = findOwnedSession(userId, voiceSessionId);
        final VoiceCommandResponse replayedResponse = findReplayedResponse(
                userId,
                idempotencyKey
        );
        if (replayedResponse != null) {
            return replayedResponse;
        }
        validateSession(session, now);

        final PendingTransferSlots previousSlots = readPendingSlots(session);
        final VoiceAnalysisResponse analysis = analyze(audio, session, previousSlots);
        return decide(userId, session, analysis, previousSlots, confirmationId, idempotencyKey, now);
    }

    /**
     * 이미 분석된 결과로 다음 행동을 정한다.
     *
     * <p>오디오가 필요한 것은 형식 검증과 분석까지다. 그 뒤의 슬롯 채우기·소유권·한도·
     * 확인·FDS·이체는 전부 {@link VoiceAnalysisResponse} 만 보고 돈다. 실시간 스트리밍은
     * 분석까지를 이미 마친 상태로 도착하므로, 여기서부터 같은 경로를 태운다.
     *
     * <p>이 분리가 없으면 스트리밍이 검증 흐름을 따로 구현하게 되고, 두 경로의 판단이
     * 어긋나는 순간 한쪽에서만 막히는 이체가 생긴다.
     */
    @Transactional
    public VoiceCommandResponse processAnalyzed(
            final Long userId,
            final Long voiceSessionId,
            final VoiceAnalysisResponse analysis,
            final String confirmationId,
            final String idempotencyKey
    ) {
        final LocalDateTime now = LocalDateTime.now();
        final VoiceSession session = findOwnedSession(userId, voiceSessionId);
        final VoiceCommandResponse replayedResponse = findReplayedResponse(
                userId,
                idempotencyKey
        );
        if (replayedResponse != null) {
            return replayedResponse;
        }
        validateSession(session, now);

        final PendingTransferSlots previousSlots = readPendingSlots(session);
        return decide(userId, session, analysis, previousSlots, confirmationId, idempotencyKey, now);
    }

    private VoiceCommandResponse decide(
            final Long userId,
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final PendingTransferSlots previousSlots,
            final String confirmationId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        final String transcript = SensitiveTextMasker.mask(analysis.transcript());
        PendingTransferSlots slots = previousSlots;
        if (session.getStatus() == VoiceSessionStatus.AWAITING_CONFIRMATION) {
            final VoiceIntent answer = resolveConfirmationAnswer(analysis);
            if (answer != null) {
                return processConfirmationResponse(
                        session,
                        analysis,
                        answer,
                        transcript,
                        previousSlots,
                        confirmationId,
                        idempotencyKey,
                        now
                );
            }
            /*
             * 확인 답으로도, 알아들을 수 있는 말로도 읽히지 않았다. 무엇을 들었는지
             * 남긴다 -- 이 자리는 실패해도 명령이 저장되지 않아, 로그가 없으면 사용자가
             * 무슨 말을 했고 무엇으로 들렸는지 알 방법이 없다.
             */
            log.info(
                    "확인 대기 중에 확인 답이 아닌 발화가 왔습니다: intent={}, sttConfidence={}, transcript={}",
                    analysis.intent(),
                    analysis.sttConfidence(),
                    transcript
            );
            /*
             * "보낼까요?"에 답이 아니라 새 명령이 왔다. 안내를 못 들었거나 마음을 바꾼
             * 것이다. 오류로 막으면 -- 화면을 보지 않는 사용자는 같은 말을 반복할 수밖에
             * 없으므로 -- 반복할수록 계속 막힌다. 앞선 확인은 포기하고 새 명령으로 받는다.
             * 옛 슬롯을 남기면 뒤이은 발화가 병합돼 엉뚱한 이체가 된다.
             */
            session.resumeActive(now);
            slots = null;   // 슬롯 없음. readPendingSlots 가 쓰는 표현과 같다.
        }
        if (analysis.intent() == VoiceIntent.HISTORY) {
            return queryHistory(session, analysis, transcript, now);
        }
        if (analysis.intent() == VoiceIntent.BALANCE) {
            return queryBalance(session, analysis, transcript, now);
        }
        validateIntent(analysis.intent());

        final TransferCommandRequest commandRequest = createCommandRequest(
                analysis,
                slots
        );
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        final TransferValidationResult validationResult = transferValidationService.validate(
                userId,
                commandRequest
        );

        if (validationResult instanceof TransferClarification clarification) {
            return clarify(
                    session,
                    voiceCommand,
                    commandRequest,
                    clarification,
                    transcript,
                    analysis.processingMs(),
                    now
            );
        }
        return awaitConfirmation(
                userId,
                session,
                voiceCommand,
                (ValidatedTransferCommand) validationResult,
                transcript,
                commandRequest.accountNumber(),
                analysis.processingMs(),
                now
        );
    }

    private VoiceCommandResponse findReplayedResponse(
            final Long userId,
            final String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        validateIdempotencyKey(idempotencyKey);
        return transferExecutionService.findCompletedResult(userId, idempotencyKey)
                .map(VoiceCommandResponse::executed)
                .orElse(null);
    }

    private void validateAudio(final MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "음성 파일 누락");
        }
        if (audio.getSize() > MAXIMUM_AUDIO_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "음성 파일 크기 초과");
        }
        final String contentType = normalizeContentType(audio.getContentType());
        if (!SUPPORTED_AUDIO_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        audioDurationValidator.validate(audio, contentType);
    }

    private String normalizeContentType(final String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private VoiceSession findOwnedSession(final Long userId, final Long voiceSessionId) {
        final VoiceSession session = voiceSessionRepository.findById(voiceSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND));
        if (!Objects.equals(session.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void validateSession(final VoiceSession session, final LocalDateTime now) {
        if (session.isExpired(now)) {
            voiceSessionExpirationService.expire(session.getId(), now);
            throw new BusinessException(ErrorCode.SLOT_EXPIRED);
        }
        if (session.getStatus() != VoiceSessionStatus.ACTIVE
                && session.getStatus() != VoiceSessionStatus.CLARIFYING
                && session.getStatus() != VoiceSessionStatus.AWAITING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
        if (session.isRetryExceeded()) {
            voiceSessionExpirationService.expire(session.getId(), now);
            throw new BusinessException(ErrorCode.RETRY_LIMIT_EXCEEDED);
        }
    }

    /**
     * 실시간 스트림이 연결될 때 지금 무엇을 되묻고 있는지 알려 준다.
     *
     * <p>재질문 중이면 이어지는 발화가 "김민수"처럼 짧다. 그 말만 놓고 전체 의도를
     * 다시 분석하면 이체라는 것을 잃어버리므로, 무엇을 물어봤는지 AI 에 미리 넘긴다.
     */
    @Transactional(readOnly = true)
    public VoiceStreamContext findStreamContext(final Long userId, final Long voiceSessionId) {
        final VoiceSession session = findOwnedSession(userId, voiceSessionId);
        return new VoiceStreamContext(
                session.getPendingIntent(),
                findExpectedSlots(readPendingSlots(session))
        );
    }

    private VoiceAnalysisResponse analyze(
            final MultipartFile audio,
            final VoiceSession session,
            final PendingTransferSlots previousSlots
    ) {
        final VoiceAnalysisRequest request = VoiceAnalysisRequest.of(
                audio.getResource(),
                "voice-" + UUID.randomUUID(),
                session.getId(),
                session.getPendingIntent(),
                findExpectedSlots(previousSlots)
        );
        return voiceAnalysisClient.analyze(request);
    }

    private List<VoiceSlot> findExpectedSlots(final PendingTransferSlots previousSlots) {
        if (previousSlots == null) {
            return List.of();
        }
        final List<VoiceSlot> expectedSlots = new ArrayList<>();
        if (previousSlots.recipientNickname() == null) {
            expectedSlots.add(VoiceSlot.RECIPIENT);
        }
        if (previousSlots.amount() == null) {
            expectedSlots.add(VoiceSlot.AMOUNT);
        }
        return List.copyOf(expectedSlots);
    }

    private void validateIntent(final VoiceIntent intent) {
        if (intent != VoiceIntent.TRANSFER) {
            throw new BusinessException(ErrorCode.INTENT_UNKNOWN);
        }
    }

    /**
     * 거래내역을 조회해 음성으로 안내한다.
     *
     * <p>조회는 돈을 움직이지 않으므로 확인 단계를 두지 않고 바로 답한다. 대신 앞선 송금
     * 슬롯이 남아 있으면 폐기한다 — 사용자가 화제를 바꾼 것이고, 남겨 두면 뒤이은 발화가
     * 옛 슬롯과 병합돼 엉뚱한 이체로 이어진다.
     */
    /**
     * 잔액을 조회해 음성으로 안내한다.
     *
     * <p>거래내역 조회와 같은 성격이다 — 돈이 움직이지 않으므로 확인 단계를 두지 않고 바로
     * 답하고, 앞선 송금 슬롯이 남아 있으면 폐기한다.
     *
     * <p>계좌 별칭은 잘못 들으면 엉뚱한 계좌 잔액을 읽어 준다. 화면으로 확인할 수 없는
     * 사용자에게는 정정할 방법이 없으므로 송금과 같은 신뢰도 기준을 적용한다.
     */
    private VoiceCommandResponse queryBalance(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final String transcript,
            final LocalDateTime now
    ) {
        validateSourceAccountConfidence(analysis.entities(), analysis.entityConfidences());
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        final BalanceResponse balance = balanceInquiryService.inquire(
                session.getUser().getId(),
                analysis.entities().sourceAccountAlias()
        );

        session.resumeActive(now);
        final VoiceCommandResponse response = VoiceCommandResponse.balance(
                session,
                balance,
                transcript
        );
        voiceCommand.completeWith(response.toVoiceMessage(), analysis.processingMs());
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    private VoiceCommandResponse queryHistory(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final String transcript,
            final LocalDateTime now
    ) {
        final VoiceEntities entities = analysis.entities();
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(
                entities.startDate(),
                entities.endDate(),
                now.toLocalDate()
        );
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        final Account account = findSourceAccount(session.getUser().getId(), null);
        final PageResponse<TransactionResponse> transactions = transactionQueryService.findAll(
                session.getUser().getId(),
                account.getId(),
                period.startDate(),
                period.endDate(),
                null,
                0,
                HISTORY_PAGE_SIZE
        );

        session.resumeActive(now);
        final VoiceCommandResponse response = VoiceCommandResponse.history(
                session,
                VoiceCommandResponse.History.of(
                        period.toVoicePhrase(now.toLocalDate()),
                        account.toVoiceName(),
                        transactions.totalElements(),
                        transactions.content().stream()
                                .map(VoiceCommandResponse.Item::from)
                                .toList()
                ),
                transcript
        );
        voiceCommand.completeWith(response.toVoiceMessage(), analysis.processingMs());
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    /**
     * 확인 대기 중인 발화가 승인인지 취소인지 정한다.
     *
     * <p>AI 가 낸 intent 를 먼저 믿는다. 그것이 확인 답이 아니면 말 자체를 본다 --
     * 확인 질문에 답하는 자리에서 "네 맞아요"를 새 명령으로 읽을 이유가 없고, STT 가
     * 한 음절을 흘리거나 GPT 가 흔들려도 사용자는 같은 말을 반복할 뿐이다. 화면을 보지
     * 않는 사용자에게는 그 반복이 유일한 수단이라 계속 막히면 빠져나올 길이 없다.
     *
     * <p><b>부정을 먼저 본다.</b> "아니요"가 "아니네요"로 잘못 적히면 그 안에 "네"가
     * 들어 있다. 긍정을 먼저 보면 취소하려던 송금이 나간다.
     *
     * <p>이 판정은 확인 대기 상태에서만 쓴다. 다른 상태에서 같은 말이 오면 평소대로
     * AI 의 intent 를 따른다.
     *
     * @return 승인도 취소도 아니면 {@code null}
     */
    private VoiceIntent resolveConfirmationAnswer(final VoiceAnalysisResponse analysis) {
        if (analysis.intent() == VoiceIntent.CANCEL) {
            return VoiceIntent.CANCEL;
        }
        if (analysis.intent() == VoiceIntent.CONFIRM) {
            return VoiceIntent.CONFIRM;
        }
        final String transcript = analysis.transcript();
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        final String spoken = transcript.replace(" ", "");
        if (containsAny(spoken, DENIAL_WORDS)) {
            return VoiceIntent.CANCEL;
        }
        if (containsAny(spoken, APPROVAL_WORDS)) {
            return VoiceIntent.CONFIRM;
        }
        return null;
    }

    private boolean containsAny(final String spoken, final List<String> words) {
        for (final String word : words) {
            if (spoken.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private VoiceCommandResponse processConfirmationResponse(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final VoiceIntent answer,
            final String transcript,
            final PendingTransferSlots pendingSlots,
            final String confirmationId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        if (answer == VoiceIntent.CANCEL) {
            return cancel(session, analysis, transcript, now);
        }
        if (answer == VoiceIntent.CONFIRM) {
            return confirm(
                    session,
                    analysis,
                    transcript,
                    pendingSlots,
                    confirmationId,
                    idempotencyKey,
                    now
            );
        }
        throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
    }

    private VoiceCommandResponse cancel(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final String transcript,
            final LocalDateTime now
    ) {
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        session.cancel(now);
        final VoiceCommandResponse response = VoiceCommandResponse.canceled(session, transcript);
        voiceCommand.completeWith(response.toVoiceMessage(), analysis.processingMs());
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    private VoiceCommandResponse confirm(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final String transcript,
            final PendingTransferSlots pendingSlots,
            final String confirmationId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        validateConfirmationChannel(idempotencyKey);
        validateIdempotencyKey(idempotencyKey);
        validateConfirmationSlots(pendingSlots);
        validateConfirmationId(pendingSlots, confirmationId);
        final Account account = findOwnedAccount(
                session.getUser().getId(),
                pendingSlots.fromAccountId()
        );
        final TransferRecipient recipient = findOwnedRecipient(
                session.getUser().getId(),
                pendingSlots.recipientId()
        );
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        voiceCommandRepository.saveAndFlush(voiceCommand);
        session.startProcessing(now);

        final TransferExecutionResult result = transferExecutionService.execute(
                ConfirmedTransferCommand.of(
                        session.getUser(),
                        account,
                        recipient,
                        voiceCommand,
                        session.getDevice(),
                        pendingSlots.amount(),
                        idempotencyKey,
                        analysis.sttConfidence()
                )
        );
        session.complete(LocalDateTime.now());
        final VoiceCommandResponse response = VoiceCommandResponse.executed(result, transcript);
        voiceCommand.completeWith(response.toVoiceMessage(), analysis.processingMs());
        return response;
    }

    /**
     * 확인 발화가 확인을 실행할 수 있는 경로로 들어왔는지 본다.
     *
     * <p>확인에는 멱등키와 {@code confirmationId} 가 필요하고, 그 교환은 REST 가
     * 담당한다. 실시간 스트림에는 두 값을 실을 자리가 없어, 확인 발화가 스트림으로
     * 올라오면 멱등키가 비어 여기에 닿는다.
     *
     * <p>이때 "요청을 처리하지 못했어요" 로 끝내면 화면을 보지 않는 사용자는 무엇이
     * 잘못됐는지 알 수 없어 같은 말을 반복하고, 그러다 세션이 만료된다. 무엇을 하면
     * 되는지 말해 주는 코드로 바꾼다.
     */
    private void validateConfirmationChannel(final String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.CONFIRMATION_KEY_MISSING);
        }
    }

    private void validateIdempotencyKey(final String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "멱등성 키 누락");
        }
        try {
            UUID.fromString(idempotencyKey);
        } catch (final IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "멱등성 키 형식 오류");
        }
    }

    private void validateConfirmationSlots(final PendingTransferSlots pendingSlots) {
        if (pendingSlots == null
                || pendingSlots.amount() == null
                || pendingSlots.recipientId() == null
                || pendingSlots.fromAccountId() == null
                || pendingSlots.confirmationId() == null) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    private void validateConfirmationId(
            final PendingTransferSlots pendingSlots,
            final String confirmationId
    ) {
        if (confirmationId == null || confirmationId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_ID, "확인 ID 누락");
        }
        if (!Objects.equals(pendingSlots.confirmationId(), confirmationId.trim())) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_ID, "확인 ID 불일치");
        }
    }

    private Account findOwnedAccount(final Long userId, final Long accountId) {
        return transferTargetResolver.resolveOwnedAccount(userId, accountId);
    }

    private TransferRecipient findOwnedRecipient(final Long userId, final Long recipientId) {
        return transferTargetResolver.resolveOwnedRecipient(userId, recipientId);
    }

    private TransferCommandRequest createCommandRequest(
            final VoiceAnalysisResponse analysis,
            final PendingTransferSlots previousSlots
    ) {
        final VoiceEntities entities = analysis.entities();
        final VoiceEntityConfidences confidences = analysis.entityConfidences();
        validateSourceAccountConfidence(entities, confidences);

        if (previousSlots == null) {
            return TransferCommandRequest.of(
                    entities.amount(),
                    entities.recipient(),
                    spokenAccountNumberOf(analysis),
                    spokenBankCodeOf(analysis),
                    entities.sourceAccountAlias(),
                    analysis.sttConfidence(),
                    analysis.intentConfidence(),
                    confidences.amount(),
                    confidences.recipient()
            );
        }
        return mergeCommandRequest(analysis, previousSlots);
    }

    private TransferCommandRequest mergeCommandRequest(
            final VoiceAnalysisResponse analysis,
            final PendingTransferSlots previousSlots
    ) {
        final VoiceEntities entities = analysis.entities();
        final VoiceEntityConfidences confidences = analysis.entityConfidences();
        final Long amount = chooseValue(entities.amount(), previousSlots.amount());
        final String recipient = chooseValue(
                entities.recipient(),
                previousSlots.recipientNickname()
        );
        final String sourceAccountAlias = chooseValue(
                entities.sourceAccountAlias(),
                previousSlots.sourceAccountAlias()
        );
        return TransferCommandRequest.of(
                amount,
                recipient,
                chooseValue(spokenAccountNumberOf(analysis), previousSlots.accountNumber()),
                chooseValue(spokenBankCodeOf(analysis), previousSlots.bankCode()),
                sourceAccountAlias,
                analysis.sttConfidence(),
                analysis.intentConfidence(),
                chooseConfidence(entities.amount(), confidences.amount(), previousSlots.amount()),
                chooseConfidence(
                        entities.recipient(),
                        confidences.recipient(),
                        previousSlots.recipientNickname()
                )
        );
    }

    /**
     * 발화에서 계좌번호를 뽑는다.
     *
     * <p>AI 계약에는 계좌번호 엔티티가 없어 transcript 에서 직접 읽는다. 자릿수를 지어내지
     * 않도록 파서가 엄격히 거른다 - 못 읽으면 비운 채로 두고 백엔드가 되묻는다.
     */
    private String spokenAccountNumberOf(final VoiceAnalysisResponse analysis) {
        return spokenAccountNumberParser.parse(analysis.transcript()).orElse(null);
    }

    private String spokenBankCodeOf(final VoiceAnalysisResponse analysis) {
        final String bankName = analysis.entities().bankName();
        return bankDirectory.findCode(bankName).orElse(null);
    }

    private <T> T chooseValue(final T currentValue, final T previousValue) {
        if (currentValue != null) {
            return currentValue;
        }
        return previousValue;
    }

    private BigDecimal chooseConfidence(
            final Object currentValue,
            final BigDecimal currentConfidence,
            final Object previousValue
    ) {
        if (currentValue != null) {
            return currentConfidence;
        }
        if (previousValue != null) {
            return BigDecimal.ONE;
        }
        return null;
    }

    private void validateSourceAccountConfidence(
            final VoiceEntities entities,
            final VoiceEntityConfidences confidences
    ) {
        if (entities.sourceAccountAlias() == null || entities.sourceAccountAlias().isBlank()) {
            return;
        }
        if (confidences.sourceAccountAlias() == null
                || confidences.sourceAccountAlias().compareTo(MINIMUM_CONFIDENCE) < 0) {
            throw new BusinessException(ErrorCode.LOW_CONFIDENCE);
        }
    }

    private VoiceCommand createVoiceCommand(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis
    ) {
        return VoiceCommand.builder()
                .session(session)
                .user(session.getUser())
                .sttText(SensitiveTextMasker.mask(analysis.transcript()))
                .sttConfidence(analysis.sttConfidence())
                .intent(analysis.intent())
                .entities(writeJson(maskEntities(analysis.entities())))
                .nluConfidence(analysis.intentConfidence())
                .build();
    }

    private VoiceEntities maskEntities(final VoiceEntities entities) {
        return new VoiceEntities(
                entities.amount(),
                SensitiveTextMasker.mask(entities.recipient()),
                SensitiveTextMasker.mask(entities.sourceAccountAlias()),
                entities.bankName(),
                entities.startDate(),
                entities.endDate()
        );
    }

    private VoiceCommandResponse clarify(
            final VoiceSession session,
            final VoiceCommand voiceCommand,
            final TransferCommandRequest commandRequest,
            final TransferClarification clarification,
            final String transcript,
            final int processingMs,
            final LocalDateTime now
    ) {
        final PendingTransferSlots pendingSlots = createClarifyingSlots(
                commandRequest,
                clarification.missingSlots()
        );
        session.clarify(VoiceIntent.TRANSFER, writeJson(pendingSlots), now);
        voiceCommand.markClarify(clarification.voiceMessage(), processingMs);
        voiceCommandRepository.save(voiceCommand);
        return VoiceCommandResponse.clarifying(
                session,
                clarification.missingSlots(),
                transcript,
                clarification.voiceMessage()
        );
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private PendingTransferSlots createClarifyingSlots(
            final TransferCommandRequest request,
            final List<TransferSlot> missingSlots
    ) {
        Long amount = request.amount();
        String recipient = request.recipient();
        if (missingSlots.contains(TransferSlot.AMOUNT)) {
            amount = null;
        }
        if (missingSlots.contains(TransferSlot.RECIPIENT)) {
            recipient = null;
        }
        /*
         * 계좌번호는 되물음 대상이 아니면 그대로 지킨다. 금액만 다시 물었는데 계좌번호를
         * 잃으면 사용자가 열 자리 숫자를 처음부터 다시 말해야 한다.
         *
         * 수취인을 되물을 때도 <b>절반만 들은 계좌는 지키고</b>, 아무것도 못 들었을 때만
         * 버린다. 은행만 듣고 지워 버리면 사용자가 계좌번호를 대답해도 은행이 없어 또
         * 되묻게 되고, 한 번에 둘 다 말할 때까지 같은 질문이 반복된다.
         *
         * 반대로 둘 다 없는 채로 이름만 다시 묻는 상황에서는 남길 것이 없다. 이때 낡은
         * 계좌번호를 들고 있으면 사용자가 이름으로 답해도 계좌 쪽이 우선해 엉뚱한 곳으로
         * 나간다.
         */
        String accountNumber = request.accountNumber();
        String bankCode = request.bankCode();
        final boolean heardNothingAboutAccount =
                isBlank(accountNumber) && isBlank(bankCode);
        if (missingSlots.contains(TransferSlot.RECIPIENT) && heardNothingAboutAccount) {
            accountNumber = null;
            bankCode = null;
        }
        return PendingTransferSlots.clarifying(
                amount,
                recipient,
                accountNumber,
                bankCode,
                request.sourceAccountAlias()
        );
    }

    private VoiceCommandResponse awaitConfirmation(
            final Long userId,
            final VoiceSession session,
            final VoiceCommand voiceCommand,
            final ValidatedTransferCommand validatedCommand,
            final String transcript,
            final String spokenAccountNumber,
            final int processingMs,
            final LocalDateTime now
    ) {
        final Account account = findSourceAccount(userId, validatedCommand.sourceAccountAlias());
        final TransferRecipient recipient = validatedCommand.recipient();
        final String confirmationId = UUID.randomUUID().toString();
        final PendingTransferSlots pendingSlots = PendingTransferSlots.awaitingConfirmation(
                validatedCommand.amount(),
                recipient.getNickname(),
                validatedCommand.sourceAccountAlias(),
                recipient.getId(),
                account.getId(),
                confirmationId
        );
        session.awaitConfirmation(VoiceIntent.TRANSFER, writeJson(pendingSlots), now);
        final VoiceCommandResponse response = VoiceCommandResponse.awaitingConfirmation(
                session,
                confirmationId,
                account,
                recipient,
                validatedCommand.amount(),
                transcript,
                readBackDigitsOf(spokenAccountNumber)
        );
        voiceCommand.completeWith(response.toVoiceMessage(), processingMs);
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    /**
     * 확인 단계에서 읽어 줄 계좌번호.
     *
     * <p>등록해 둔 이름으로 보내는 경우에는 비운다 — 그때는 사용자가 부른 이름을 그대로
     * 되읽어 주는 편이 알아듣기 쉽다.
     */
    private String readBackDigitsOf(final String spokenAccountNumber) {
        if (spokenAccountNumber == null || spokenAccountNumber.isBlank()) {
            return null;
        }
        return spokenAccountNumberParser.toSpokenDigits(spokenAccountNumber);
    }

    private Account findSourceAccount(final Long userId, final String accountAlias) {
        return transferTargetResolver.resolveSourceAccount(userId, accountAlias);
    }

    private PendingTransferSlots readPendingSlots(final VoiceSession session) {
        if (session.getStatus() != VoiceSessionStatus.CLARIFYING
                && session.getStatus() != VoiceSessionStatus.AWAITING_CONFIRMATION) {
            return null;
        }
        if (session.getPendingSlots() == null || session.getPendingSlots().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
        try {
            return objectMapper.readValue(session.getPendingSlots(), PendingTransferSlots.class);
        } catch (final JacksonException exception) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE, "슬롯 역직렬화 실패");
        }
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "JSON 직렬화 실패");
        }
    }
}
