package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.fds.application.FdsAssessmentService;
import com.movi_backend.domain.fds.dto.FdsEvaluationCommand;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferResult;
import com.movi_backend.domain.transfer.dto.PreparedTransfer;
import com.movi_backend.domain.transfer.dto.TransferConfirmation;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.response.TransferResponse;
import com.movi_backend.domain.transfer.infrastructure.OpenBankingClient;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이체 실행 흐름 조립.
 *
 * <p>이 클래스에는 {@code @Transactional}이 없다. 일부러 그렇게 뒀다. 단계마다 트랜잭션을 끊어야
 * 아래 두 가지가 지켜진다.
 *
 * <ul>
 *   <li>FDS·오픈뱅킹 응답을 기다리는 동안 DB 커넥션과 락을 붙잡고 있지 않는다.</li>
 *   <li>상태를 먼저 확정한 뒤 알림을 보낸다. 알림 실패가 확정된 상태를 롤백하지 못한다.</li>
 * </ul>
 *
 * <p>흐름:
 * <pre>
 * 멱등성 확인 → PENDING 생성 → 잔액 조회 → RISK_REVIEW → FDS 평가
 *      ├─ HIGH/BLOCK → 오픈뱅킹 호출 안 함 → HOLD 확정
 *      │                → 보호자 + 본인에게 고위험 감지 알림
 *      │                → 본인 응답 대기
 *      │                     ├─ 네    → confirm() → 오픈뱅킹 이체 → COMPLETED → 보호자 통보
 *      │                     ├─ 아니요 → decline() → BLOCKED → 보호자 통보
 *      │                     └─ 무응답 → 확인 시간 초과 → BLOCKED → 보호자 통보
 *      └─ LOW/MEDIUM → 오픈뱅킹 이체 → COMPLETED → (MEDIUM이면 보호자 통보)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferFacade {

    private static final String FAIL_REASON_BALANCE = "INSUFFICIENT_BALANCE";
    private static final String FAIL_REASON_ASSESSMENT = "FDS_ASSESSMENT_FAILED";
    private static final String FAIL_REASON_OPENBANKING = "OPENBANKING_TRANSFER_FAILED";

    private final TransferService transferService;
    private final FdsAssessmentService fdsAssessmentService;
    private final TransferRiskAlertService transferRiskAlertService;
    private final OpenBankingClient openBankingClient;

    /** 이체 요청. 고위험이면 실행하지 않고 확인 대기 상태로 응답한다. */
    public TransferResponse execute(final Long userId, final TransferExecuteRequest request) {
        final Optional<TransferResponse> processed =
                transferService.findProcessed(request.idempotencyKey());
        if (processed.isPresent()) {
            return processed.get();
        }

        final PreparedTransfer prepared =
                transferService.create(userId, TransferCreateCommand.from(request));
        final long balanceBefore = inquireBalance(prepared);
        validateBalance(prepared, balanceBefore);

        transferService.startRiskReview(prepared.transferId());
        final FdsAssessment assessment = assess(prepared, balanceBefore, request);

        if (assessment.getDecision() == FdsDecision.BLOCK) {
            return holdForConfirmation(prepared, assessment);
        }
        return settle(prepared, assessment.getRiskLevel(), assessment.requiresGuardianAlert());
    }

    /**
     * 사용자가 "네"라고 답했다. 그제서야 실제 이체를 실행한다.
     *
     * <p>같은 확인 요청이 두 번 들어와도 이체는 한 번만 나간다. 음성은 중복 발화가 잦다.
     */
    public TransferResponse confirm(final Long userId, final Long transferId) {
        final TransferConfirmation confirmation =
                transferService.prepareConfirmation(userId, transferId);
        if (confirmation.isAlreadyCompleted()) {
            return confirmation.completedResponse();
        }
        if (confirmation.expired()) {
            transferRiskAlertService.transferBlocked(userId, transferId);
            throw new BusinessException(ErrorCode.TRANSFER_CONFIRMATION_EXPIRED);
        }

        final TransferResponse response =
                settle(confirmation.prepared(), RiskLevel.HIGH, false);
        transferRiskAlertService.highRiskConfirmed(userId, transferId);
        return response;
    }

    /** 사용자가 "아니요"라고 답했다. 차단으로 확정하고 보호자에게 알린다. */
    public TransferResponse decline(final Long userId, final Long transferId) {
        final TransferResponse response = transferService.decline(userId, transferId);
        transferRiskAlertService.transferBlocked(userId, transferId);
        return response;
    }

    /**
     * 고위험 처리. <b>오픈뱅킹을 호출하지 않는다.</b>
     *
     * <p>상태 확정(트랜잭션 A)과 알림(트랜잭션 B)을 분리한다. 알림이 실패해도 확인 대기 상태는
     * 그대로 남고, 사용자가 답하지 않으면 시간이 지나 차단으로 확정된다.
     *
     * <p>보호자와 본인 양쪽에 알린다. 본인이 요청하지 않은 이체라면 본인이 가장 먼저 알아야 하고,
     * 전화로 지시받는 중이라면 보호자가 알아야 한다.
     */
    private TransferResponse holdForConfirmation(
            final PreparedTransfer prepared,
            final FdsAssessment assessment
    ) {
        final TransferResponse response =
                transferService.hold(prepared.transferId(), assessment.getRiskLevel());
        transferRiskAlertService.highRiskDetected(prepared.userId(), prepared.transferId());
        return response;
    }

    /** 실제 송금과 완료 처리. 고위험 재확인 경로와 일반 경로가 같은 코드를 쓴다. */
    private TransferResponse settle(
            final PreparedTransfer prepared,
            final RiskLevel riskLevel,
            final boolean notifyGuardians
    ) {
        final OpenBankingTransferResult result = executeOpenBankingTransfer(prepared);
        if (!result.successful()) {
            transferService.fail(prepared.transferId(), FAIL_REASON_OPENBANKING);
            throw new BusinessException(ErrorCode.TRANSFER_EXECUTION_FAILED);
        }

        final TransferResponse response = transferService.complete(
                prepared.transferId(), riskLevel, LocalDateTime.now());
        if (notifyGuardians) {
            transferRiskAlertService.mediumRiskCompleted(prepared.userId(), prepared.transferId());
        }
        return response;
    }

    /** 평가에 실패하면 이체를 통과시키지 않는다. 평가 불가는 곧 위험이다. */
    private FdsAssessment assess(
            final PreparedTransfer prepared,
            final long balanceBefore,
            final TransferExecuteRequest request
    ) {
        try {
            return fdsAssessmentService.evaluate(FdsEvaluationCommand.of(
                    prepared.transferId(),
                    prepared.userId(),
                    prepared.amount(),
                    balanceBefore,
                    prepared.requestedAt(),
                    prepared.recipientTransferCount(),
                    request.trustedDeviceOrFalse(),
                    request.sttConfidenceOrDefault()
            ));
        } catch (final BusinessException exception) {
            transferService.fail(prepared.transferId(), FAIL_REASON_ASSESSMENT);
            throw exception;
        }
    }

    private long inquireBalance(final PreparedTransfer prepared) {
        try {
            return openBankingClient.inquireBalance(prepared.fromFintechUseNum());
        } catch (final RuntimeException exception) {
            log.warn("잔액 조회 실패 transferId={} type={}",
                    prepared.transferId(), exception.getClass().getSimpleName());
            transferService.fail(prepared.transferId(), FAIL_REASON_OPENBANKING);
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED);
        }
    }

    private void validateBalance(final PreparedTransfer prepared, final long balanceBefore) {
        if (balanceBefore >= prepared.amount()) {
            return;
        }
        transferService.fail(prepared.transferId(), FAIL_REASON_BALANCE);
        throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
    }

    private OpenBankingTransferResult executeOpenBankingTransfer(final PreparedTransfer prepared) {
        try {
            return openBankingClient.transfer(OpenBankingTransferCommand.of(
                    prepared.transferId(),
                    prepared.fromFintechUseNum(),
                    prepared.toBankCode(),
                    prepared.toAccountNum(),
                    prepared.toHolderName(),
                    prepared.amount()
            ));
        } catch (final RuntimeException exception) {
            log.warn("오픈뱅킹 이체 실패 transferId={} type={}",
                    prepared.transferId(), exception.getClass().getSimpleName());
            return OpenBankingTransferResult.failure();
        }
    }
}
