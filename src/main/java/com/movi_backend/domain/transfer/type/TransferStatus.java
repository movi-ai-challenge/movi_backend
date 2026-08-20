package com.movi_backend.domain.transfer.type;

import java.util.Set;

/**
 * 이체 상태.
 *
 * <p>전이는 아래 방향으로만 허용한다. 특히 {@code COMPLETED} 이후에는 어떤 상태로도 가지 않는다.
 *
 * <pre>
 * PENDING → RISK_REVIEW → COMPLETED
 *                       → HOLD → COMPLETED   (본인 재확인 후 진행)
 *                              → BLOCKED     (본인 거절 · 확인 시간 초과)
 *                              → FAILED      (재확인 후 외부 연동 실패)
 *                       → BLOCKED
 *         → FAILED / CANCELED
 * </pre>
 *
 * <p>{@code HOLD}는 고위험으로 감지했지만 아직 사용자에게 다시 물어보는 중인 상태다.
 * <b>이 상태에서는 오픈뱅킹을 호출하지 않았다.</b> 돈은 아직 움직이지 않았다.
 */
public enum TransferStatus {

    /** 이체 요청 접수. 아직 위험 평가 전 */
    PENDING,

    /** FDS 위험도 평가 중 */
    RISK_REVIEW,

    /** 고위험 감지 후 본인 재확인 대기. 오픈뱅킹 미호출 */
    HOLD,

    /** 이체 완료 (최종 상태) */
    COMPLETED,

    /** 차단 (최종 상태) */
    BLOCKED,

    /** 외부 연동 실패 등으로 실패 (최종 상태) */
    FAILED,

    /** 사용자 취소 (최종 상태) */
    CANCELED;

    private static final Set<TransferStatus> FROM_PENDING =
            Set.of(RISK_REVIEW, FAILED, CANCELED);
    private static final Set<TransferStatus> FROM_RISK_REVIEW =
            Set.of(COMPLETED, HOLD, BLOCKED, FAILED);
    private static final Set<TransferStatus> FROM_HOLD =
            Set.of(COMPLETED, BLOCKED, FAILED, CANCELED);

    /** 더 이상 전이할 수 없는 상태인지 여부 */
    public boolean isFinal() {
        return this == COMPLETED || this == BLOCKED || this == FAILED || this == CANCELED;
    }

    /** 사용자 재확인을 기다리는 상태인지 여부 */
    public boolean awaitsConfirmation() {
        return this == HOLD;
    }

    /** {@code next}로 전이할 수 있는지 여부 */
    public boolean canTransitionTo(final TransferStatus next) {
        if (this == PENDING) {
            return FROM_PENDING.contains(next);
        }
        if (this == RISK_REVIEW) {
            return FROM_RISK_REVIEW.contains(next);
        }
        if (this == HOLD) {
            return FROM_HOLD.contains(next);
        }
        return false;
    }
}
