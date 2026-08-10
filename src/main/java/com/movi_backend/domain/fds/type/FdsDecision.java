package com.movi_backend.domain.fds.type;

/**
 * FDS 평가 결과에 따른 처리 방침.
 *
 * <p>보호자 <b>승인</b>(사전 차단)은 MVP 범위에서 제외했다. 중위험 이체는 대기 없이 진행되고
 * 보호자에게는 사후 통보만 나간다.
 */
public enum FdsDecision {

    /** 저위험 — 즉시 이체 */
    ALLOW,

    /** 중위험 — 이체는 진행하고 보호자에게 통보 */
    ALLOW_WITH_ALERT,

    /** 고위험 — 이체 차단 후 보호자에게 통보 */
    BLOCK;

    /** 위험도에 대응하는 기본 처리 방침 */
    public static FdsDecision from(final RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.LOW) {
            return ALLOW;
        }
        if (riskLevel == RiskLevel.MEDIUM) {
            return ALLOW_WITH_ALERT;
        }
        return BLOCK;
    }

    /** 보호자에게 알림을 보내야 하는 결정인지 여부 */
    public boolean requiresGuardianAlert() {
        return this == ALLOW_WITH_ALERT || this == BLOCK;
    }
}
