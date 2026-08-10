package com.movi_backend.domain.guardian.type;

/**
 * 보호자 연결 상태.
 */
public enum GuardianLinkStatus {

    /** 요청됨 */
    REQUESTED,
    /** 연결됨 */
    ACTIVE,
    /** 거절됨 */
    REJECTED,
    /** 해제됨 */
    REVOKED
}
