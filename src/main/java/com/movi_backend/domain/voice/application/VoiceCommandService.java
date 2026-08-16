package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.application.TransferValidationService;
import com.movi_backend.domain.transfer.application.TransferExecutionService;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.voice.application.model.PendingTransferSlots;
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
    private static final List<String> SUPPORTED_AUDIO_TYPES = List.of(
            "audio/webm",
            "audio/wav",
            "audio/x-wav",
            "audio/wave"
    );

    private final VoiceSessionRepository voiceSessionRepository;
    private final VoiceCommandRepository voiceCommandRepository;
    private final VoiceAnalysisClient voiceAnalysisClient;
    private final TransferValidationService transferValidationService;
    private final TransferExecutionService transferExecutionService;
    private final AccountRepository accountRepository;
    private final TransferRecipientRepository transferRecipientRepository;
    private final ObjectMapper objectMapper;

    public VoiceCommandResponse process(
            final Long userId,
            final Long voiceSessionId,
            final MultipartFile audio
    ) {
        return process(userId, voiceSessionId, audio, null);
    }

    @Transactional
    public VoiceCommandResponse process(
            final Long userId,
            final Long voiceSessionId,
            final MultipartFile audio,
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
        if (session.getStatus() == VoiceSessionStatus.AWAITING_CONFIRMATION) {
            return processConfirmationResponse(
                    session,
                    analysis,
                    previousSlots,
                    idempotencyKey,
                    now
            );
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
                    analysis.processingMs(),
                    now
            );
        }
        return awaitConfirmation(
                userId,
                session,
                voiceCommand,
                (ValidatedTransferCommand) validationResult,
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
            if (!session.isClosed()) {
                session.expire(now);
            }
            throw new BusinessException(ErrorCode.SLOT_EXPIRED);
        }
        if (session.getStatus() != VoiceSessionStatus.ACTIVE
                && session.getStatus() != VoiceSessionStatus.CLARIFYING
                && session.getStatus() != VoiceSessionStatus.AWAITING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
        if (session.isRetryExceeded()) {
            session.expire(now);
            throw new BusinessException(ErrorCode.RETRY_LIMIT_EXCEEDED);
        }
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

    private VoiceCommandResponse processConfirmationResponse(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final PendingTransferSlots pendingSlots,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        if (analysis.intent() == VoiceIntent.CANCEL) {
            return cancel(session, analysis, now);
        }
        if (analysis.intent() == VoiceIntent.CONFIRM) {
            return confirm(session, analysis, pendingSlots, idempotencyKey, now);
        }
        throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
    }

    private VoiceCommandResponse cancel(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final LocalDateTime now
    ) {
        final VoiceCommand voiceCommand = createVoiceCommand(session, analysis);
        session.cancel(now);
        final VoiceCommandResponse response = VoiceCommandResponse.canceled(session);
        voiceCommand.completeWith(response.toVoiceMessage(), analysis.processingMs());
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    private VoiceCommandResponse confirm(
            final VoiceSession session,
            final VoiceAnalysisResponse analysis,
            final PendingTransferSlots pendingSlots,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateConfirmationSlots(pendingSlots);
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
        final VoiceCommandResponse response = VoiceCommandResponse.executed(result);
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

    private Account findOwnedAccount(final Long userId, final Long accountId) {
        final Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!Objects.equals(account.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
    }

    private TransferRecipient findOwnedRecipient(final Long userId, final Long recipientId) {
        final TransferRecipient recipient = transferRecipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!Objects.equals(recipient.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return recipient;
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
        return VoiceCommandResponse.clarifying(session, clarification.missingSlots());
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
        return PendingTransferSlots.clarifying(
                amount,
                recipient,
                request.sourceAccountAlias()
        );
    }

    private VoiceCommandResponse awaitConfirmation(
            final Long userId,
            final VoiceSession session,
            final VoiceCommand voiceCommand,
            final ValidatedTransferCommand validatedCommand,
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
                validatedCommand.amount()
        );
        voiceCommand.completeWith(response.toVoiceMessage(), processingMs);
        voiceCommandRepository.save(voiceCommand);
        return response;
    }

    private Account findSourceAccount(final Long userId, final String accountAlias) {
        final Account account;
        if (accountAlias == null || accountAlias.isBlank()) {
            account = accountRepository.findByUserIdAndPrimaryTrue(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_ACCOUNT_NOT_SET));
        } else {
            account = accountRepository.findByUserIdAndAlias(userId, accountAlias.trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
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
