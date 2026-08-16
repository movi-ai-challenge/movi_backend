package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
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
import com.movi_backend.domain.transfer.application.port.TransferExecutionPort;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TransferExecutionService {

    private static final int COLD_START_TRANSFER_COUNT = 3;

    private final TransferRepository transferRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final UserTransferProfileRepository userTransferProfileRepository;
    private final FdsAssessmentRepository fdsAssessmentRepository;
    private final FdsAssessmentClient fdsAssessmentClient;
    private final FdsAssessmentResponseValidator responseValidator;
    private final TransferExecutionPort transferExecutionPort;
    private final TransferRiskAlertPort transferRiskAlertPort;
    private final ObjectMapper objectMapper;

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
        final Optional<Transfer> existingTransfer = transferRepository
                .findByIdempotencyKeyAndUserId(command.idempotencyKey(), command.user().getId());
        if (existingTransfer.isPresent()) {
            return resolveExisting(existingTransfer.get());
        }

        validateConnection(command.fromAccount().getConnection());
        final BalanceSnapshot balanceSnapshot = findLatestBalance(command.fromAccount().getId());
        if (balanceSnapshot.getAvailableAmount() < command.amount()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        final Transfer transfer = createTransfer(command);
        transferRepository.saveAndFlush(transfer);
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
            transferRiskAlertPort.send(transfer, assessment);
            return TransferExecutionResult.of(transfer, assessment);
        }

        if (!executeTransfer(transfer)) {
            return TransferExecutionResult.of(transfer, assessment);
        }
        final LocalDateTime completedAt = LocalDateTime.now();
        transfer.complete(completedAt);
        command.recipient().recordTransfer(completedAt);
        if (response.decision().requiresGuardianAlert()) {
            transferRiskAlertPort.send(transfer, assessment);
        }
        return TransferExecutionResult.of(transfer, assessment);
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

    private void validateConnection(final OpenbankingConnection connection) {
        if (connection == null) {
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }
        if (!connection.isUsable(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONNECTION_EXPIRED);
        }
    }

    private BalanceSnapshot findLatestBalance(final Long accountId) {
        return balanceSnapshotRepository.findTopByAccountIdOrderByFetchedAtDesc(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED));
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
                transfer.getRequestedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
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
            return Arrays.stream(objectMapper.readValue(commonHours, int[].class))
                    .boxed()
                    .toList();
        } catch (final JacksonException exception) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "FDS 프로필 형식 오류");
        }
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

    private boolean executeTransfer(final Transfer transfer) {
        try {
            transferExecutionPort.execute(transfer);
        } catch (final RuntimeException exception) {
            transfer.fail("오픈뱅킹 이체 실패");
            return false;
        }
        return true;
    }
}
