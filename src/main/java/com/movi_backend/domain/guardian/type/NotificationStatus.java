package com.movi_backend.domain.guardian.type;

/**
 * 알림 발송 상태.
 */
public enum NotificationStatus {

    /** 발송 대기 */
    QUEUED,
    /** 발송 완료 */
    SENT,
    /** 발송 실패 */
    FAILED
}
