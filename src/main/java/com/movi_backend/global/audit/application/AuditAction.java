package com.movi_backend.global.audit.application;

/**
 * 감사 로그 action 값.
 *
 * <p>문자열을 호출부마다 새로 적으면 오타 하나로 이력 추적이 끊긴다. 여기서만 정의한다.
 */
public final class AuditAction {

    public static final String GUARDIAN_LINK_REQUESTED = "GUARDIAN_LINK_REQUESTED";
    public static final String GUARDIAN_LINK_APPROVED = "GUARDIAN_LINK_APPROVED";
    public static final String GUARDIAN_LINK_REJECTED = "GUARDIAN_LINK_REJECTED";
    public static final String TRANSFER_COMPLETED = "TRANSFER_COMPLETED";
    /** 고위험으로 감지해 본인 확인을 요청함 */
    public static final String TRANSFER_HELD = "TRANSFER_HELD";
    /** 본인이 고위험 이체를 재확인함. 누가 언제 승낙했는지는 사후 분쟁의 핵심 근거다 */
    public static final String TRANSFER_CONFIRMED = "TRANSFER_CONFIRMED";
    /** 본인이 고위험 이체를 거절함 */
    public static final String TRANSFER_DECLINED = "TRANSFER_DECLINED";
    public static final String TRANSFER_BLOCKED = "TRANSFER_BLOCKED";

    public static final String RESOURCE_GUARDIAN_LINK = "GUARDIAN_LINK";
    public static final String RESOURCE_TRANSFER = "TRANSFER";

    private AuditAction() {
    }
}
