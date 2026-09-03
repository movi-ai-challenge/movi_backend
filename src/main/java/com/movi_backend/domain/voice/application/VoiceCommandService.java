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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class VoiceCommandService {

    private static final long MAXIMUM_AUDIO_SIZE = 5L * 1024L * 1024L;
    private static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.80");

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
        if (session.getStatus() == VoiceSessionStatus.AWAITING_CONFIRMATION) {
            return processConfirmationResponse(
                    session,
                    analysis,
                    transcript,
                    previousSlots,
                    confirmationId,
                    idempotencyKey,
                    now
            );
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
                previousSlots
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

    private VoiceCommandResponse processConfirmationResponse(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final String transcript,
            final PendingTransferSlots pendingSlots,
            final String confirmationId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        if (analysis.intent() == VoiceIntent.CANCEL) {
            return cancel(session, analysis, transcript, now);
        }
        if (analysis.intent() == VoiceIntent.CONFIRM) {
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
                transcript
        );
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
         */
        String accountNumber = request.accountNumber();
        String bankCode = request.bankCode();
        if (missingSlots.contains(TransferSlot.RECIPIENT)) {
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
                transcript
        );
        voiceCommand.completeWith(response.toVoiceMessage(), processingMs);
        voiceCommandRepository.save(voiceCommand);
        return response;
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
