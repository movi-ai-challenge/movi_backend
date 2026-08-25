package com.movi_backend.domain.account.type;

/**
 * 오픈뱅킹 연결 상태.
 */
public enum ConnectionStatus {

    /** 정상 */
    ACTIVE,
    /** 만료 */
    EXPIRED,
    /** 해지 */
    REVOKED
}
