package com.movi_backend.domain.guardian.type;

/**
 * 보호자 연결 상태.
 */
public enum GuardianLinkStatus {

    /** 연결됨. 회원가입 시 보호자 전화번호를 입력하면 확인 절차 없이 바로 이 상태로 생성된다. */
    ACTIVE,
    /** 해제됨 */
    REVOKED
}
