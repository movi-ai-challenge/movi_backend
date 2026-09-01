package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.application.port.OpenBankingTransferPort;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.client.FdsAssessmentClient;
import com.movi_backend.domain.fds.client.FdsAssessmentResponseValidator;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsContextFeature;
import com.movi_backend.domain.fds.client.dto.FdsProfileFeature;
import com.movi_backend.domain.fds.client.dto.FdsRecipientFeature;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferExecutionService {

    private static final int COLD_START_TRANSFER_COUNT = 3;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final TransferRepository transferRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceInquiryService balanceInquiryService;
    private final EntityManager entityManager;
    private final UserTransferProfileRepository userTransferProfileRepository;
    private final FdsAssessmentRepository fdsAssessmentRepository;
    private final FdsAssessmentClient fdsAssessmentClient;
    private final FdsAssessmentResponseValidator responseValidator;
    private final OpenBankingTransferPort openBankingTransferPort;
    private final TransferRiskAlertPort transferRiskAlertPort;
    private final TransferProperties transferProperties;
    private final ObjectMapper objectMapper;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    @Transactional(readOnly = true)
    public Optional<TransferExecutionResult> findCompletedResult(
            final Long userId,
            final String idempotencyKey
    ) {
        return transferRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId)
                .filter(transfer -> transfer.getStatus().isFinal())
                .map(transfer -> TransferExecutionResult.of(
                        transfer,
                        findAssessment(transfer.getId())
                ));
    }

    @Transactional
    public TransferExecutionResult execute(final ConfirmedTransferCommand command) {
        // 반드시 멱등성 조회보다 먼저 잠근다. 대기한 요청이 선행 트랜잭션의 결과를 조회해야 한다.
        lockUser(command.user().getId());
        final Optional<Transfer> existingTransfer = transferRepository
                .findLockedByIdempotencyKeyAndUserId(
                        command.idempotencyKey(),
                        command.user().getId()
                );
        if (existingTransfer.isPresent()) {
            return resolveExisting(existingTransfer.get());
        }

        final BalanceSnapshot balanceSnapshot = balanceInquiryService.refresh(
                command.user().getId(),
                command.fromAccount()
        );
        if (balanceSnapshot.getAvailableAmount() < command.amount()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        validateDailyLimit(command);

        final Transfer transfer = createTransfer(command);
        saveTransfer(transfer);
        transfer.startRiskReview();

        final FdsAssessmentRequest request = createAssessmentRequest(
                command,
                transfer,
                balanceSnapshot
        );
        final FdsAssessmentResponse response = responseValidator.validate(
                request,
                fdsAssessmentClient.assess(request)
        );
        final FdsAssessment assessment = saveAssessment(command, transfer, request, response);

        if (response.decision() == FdsDecision.BLOCK) {
            transfer.block("고위험 거래");
            sendRiskAlert(transfer, assessment);
            return TransferExecutionResult.of(transfer, assessment);
        }

        final Optional<OpenBankingTransferResult> transferResult = executeTransfer(transfer);
        if (transferResult.isEmpty()) {
            return TransferExecutionResult.of(transfer, assessment);
        }
        final OpenBankingTransferResult executedTransfer = transferResult.get();
        final LocalDateTime completedAt = executedTransfer.tranDateTime();
        transfer.complete(completedAt);
        command.recipient().recordTransfer(completedAt);
        saveTransaction(command, balanceSnapshot, executedTransfer);
        if (response.decision().requiresGuardianAlert()) {
            sendRiskAlert(transfer, assessment);
        }
        return TransferExecutionResult.of(transfer, assessment);
    }

    private void lockUser(final Long userId) {
        final User lockedUser = entityManager.find(
                User.class,
                userId,
                LockModeType.PESSIMISTIC_WRITE
        );
        if (lockedUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateDailyLimit(final ConfirmedTransferCommand command) {
        final LocalDate today = LocalDate.now(BUSINESS_ZONE);
        final long completedAmount = transferRepository.sumAmountByUserAndStatusBetween(
                command.user().getId(),
                TransferStatus.COMPLETED,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );
        if (completedAmount > transferProperties.dailyLimit() - command.amount()) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    private void saveTransaction(
            final ConfirmedTransferCommand command,
            final BalanceSnapshot balanceSnapshot,
            final OpenBankingTransferResult transferResult
    ) {
        final long balanceAfter = transferResult.balanceAfter() != null
                ? transferResult.balanceAfter()
                : balanceSnapshot.getAvailableAmount() - command.amount();
        final Transaction transaction = Transaction.builder()
                .account(command.fromAccount())
                .tranType(TransactionType.OUT)
                .amount(command.amount())
                .balanceAfter(balanceAfter)
                .counterpartyName(command.recipient().getHolderName())
                .counterpartyAccount(command.recipient().getAccountNum())
                .tranDatetime(transferResult.tranDateTime())
                .source(TransactionSource.INTERNAL)
                .build();
        transactionRepository.save(transaction);
    }

    private void saveTransfer(final Transfer transfer) {
        try {
            transferRepository.saveAndFlush(transfer);
        } catch (final DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRANSFER);
        }
    }

    private void sendRiskAlert(
            final Transfer transfer,
            final FdsAssessment assessment
    ) {
        try {
            transferRiskAlertPort.send(transfer, assessment);
        } catch (final RuntimeException exception) {
            log.warn("보호자 위험 알림 요청 실패: transferId={}", transfer.getId());
        }
    }

    private TransferExecutionResult resolveExisting(final Transfer transfer) {
        if (!transfer.getStatus().isFinal()) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRANSFER);
        }
        return TransferExecutionResult.of(transfer, findAssessment(transfer.getId()));
    }

    private FdsAssessment findAssessment(final Long transferId) {
        return fdsAssessmentRepository.findByTransferId(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_FAILED));
    }

    private Transfer createTransfer(final ConfirmedTransferCommand command) {
        return Transfer.builder()
                .user(command.user())
                .fromAccount(command.fromAccount())
                .recipient(command.recipient())
                .voiceCommand(command.voiceCommand())
                .toBankCode(command.recipient().getBankCode())
                .toAccountNum(command.recipient().getAccountNum())
                .toHolderName(command.recipient().getHolderName())
                .amount(command.amount())
                .idempotencyKey(command.idempotencyKey())
                .build();
    }

    private FdsAssessmentRequest createAssessmentRequest(
            final ConfirmedTransferCommand command,
            final Transfer transfer,
            final BalanceSnapshot balanceSnapshot
    ) {
        return FdsAssessmentRequest.of(
                UUID.randomUUID().toString(),
                transfer.getId(),
                command.user().getId(),
                BigDecimal.valueOf(command.amount()),
                BigDecimal.valueOf(balanceSnapshot.getAvailableAmount()),
                transfer.getRequestedAt()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(BUSINESS_ZONE)
                        .toOffsetDateTime(),
                FdsRecipientFeature.of(
                        command.recipient().getTransferCount(),
                        command.recipient().isFirstTime()
                ),
                createProfileFeature(command.user().getId()),
                FdsContextFeature.of(
                        command.device() != null && command.device().isTrusted(),
                        command.sttConfidence()
                )
        );
    }

    private FdsProfileFeature createProfileFeature(final Long userId) {
        final Optional<UserTransferProfile> profile = userTransferProfileRepository.findById(userId);
        if (profile.isEmpty()
                || profile.get().getTransferCount30d() < COLD_START_TRANSFER_COUNT) {
            return FdsProfileFeature.coldStartProfile();
        }
        final UserTransferProfile savedProfile = profile.get();
        return FdsProfileFeature.of(
                BigDecimal.valueOf(savedProfile.getAvgAmount()),
                BigDecimal.valueOf(savedProfile.getMaxAmount()),
                savedProfile.getStddevAmount(),
                savedProfile.getTransferCount30d(),
                savedProfile.getDistinctRecipients30d(),
                readCommonHours(savedProfile.getCommonHours())
        );
    }

    private List<Integer> readCommonHours(final String commonHours) {
        if (commonHours == null || commonHours.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(objectMapper.readValue(unwrapJsonString(commonHours), int[].class))
                    .boxed()
                    .toList();
        } catch (final JacksonException exception) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "FDS 프로필 형식 오류");
        }
    }

    /**
     * JSON 문자열로 한 번 더 감싸인 값을 풀어 준다.
     *
     * <p>{@code common_hours}는 JSON 컬럼인데 매핑은 {@code String}이다. 이 조합은 DB마다
     * 다르게 저장된다 — MySQL은 유효한 JSON 텍스트를 배열로 파싱해 넣지만, H2는 문자열 값으로
     * 보고 {@code "[10,14,19]"}처럼 따옴표로 감싸 돌려준다.
     *
     * <p>감싸인 쪽을 그대로 {@code int[]}로 읽으면 실패하고, FDS 평가는 fail-closed 라
     * <b>송금이 전면 차단된다.</b> DB가 바뀌었다는 이유로 돈이 안 나가면 안 된다.
     */
    private String unwrapJsonString(final String commonHours) {
        final JsonNode node = objectMapper.readTree(commonHours);
        if (node.isTextual()) {
            return node.stringValue();
        }
        return commonHours;
    }

    private FdsAssessment saveAssessment(
            final ConfirmedTransferCommand command,
            final Transfer transfer,
            final FdsAssessmentRequest request,
            final FdsAssessmentResponse response
    ) {
        final FdsAssessment assessment = FdsAssessment.builder()
                .transfer(transfer)
                .user(command.user())
                .modelVersion(response.modelVersion())
                .anomalyScore(response.scores().anomalyScore())
                .riskLevel(response.riskLevel())
                .decision(response.decision())
                .features(writeFeatures(request, response))
                .latencyMs(response.latencyMs())
                .build();
        return fdsAssessmentRepository.save(assessment);
    }

    private String writeFeatures(
            final FdsAssessmentRequest request,
            final FdsAssessmentResponse response
    ) {
        final Map<String, Object> features = new LinkedHashMap<>();
        features.put("request", request);
        features.put("policyVersion", response.policyVersion());
        features.put("ruleScore", response.scores().ruleScore());
        features.put("finalRiskScore", response.scores().finalRiskScore());
        features.put("reasonCodes", response.reasonCodes());
        try {
            return objectMapper.writeValueAsString(features);
        } catch (final JacksonException exception) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "FDS 피처 저장 실패");
        }
    }

    private Optional<OpenBankingTransferResult> executeTransfer(final Transfer transfer) {
        try {
            final OpenBankingTransferCommand command = OpenBankingTransferCommand.of(
                    transfer.getIdempotencyKey(),
                    transfer.getFromAccount().getFintechUseNum(),
                    transfer.getToBankCode(),
                    sensitiveDataCrypto.decrypt(transfer.getToAccountNum()),
                    transfer.getToHolderName(),
                    transfer.getAmount()
            );
            final OpenBankingTransferResult result = openBankingTransferPort.transfer(
                    command,
                    sensitiveDataCrypto.decrypt(
                            transfer.getFromAccount().getConnection().getAccessToken()
                    )
            );
            if (result == null) {
                throw new BusinessException(ErrorCode.TRANSFER_EXECUTION_FAILED);
            }
            return Optional.of(result);
        } catch (final RuntimeException exception) {
            transfer.fail("오픈뱅킹 이체 실패");
            return Optional.empty();
        }
    }
}
