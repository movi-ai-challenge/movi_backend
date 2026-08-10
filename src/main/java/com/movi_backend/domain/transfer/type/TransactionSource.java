package com.movi_backend.domain.transfer.type;

/**
 * 거래 내역 출처.
 */
public enum TransactionSource {

    /** 오픈뱅킹 조회 */
    OPENBANKING,
    /** 서비스 내 이체 */
    INTERNAL
}
