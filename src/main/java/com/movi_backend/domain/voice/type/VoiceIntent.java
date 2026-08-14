package com.movi_backend.domain.voice.type;

/**
 * 음성 명령 의도. AI Voice API가 추출한 값을 그대로 받는다.
 *
 * <p>MVP 사용 범위는 {@code BALANCE}, {@code TRANSFER}, {@code HISTORY}, {@code CONFIRM},
 * {@code CANCEL}, {@code UNKNOWN}이다. 계약은 docs/ai-api-contract.md 2.2절을 따른다.
 */
public enum VoiceIntent {

    /** 잔액 조회 */
    BALANCE,

    /** 이체 */
    TRANSFER,

    /** 거래내역 조회 */
    HISTORY,

    /** 확인 발화. 대기 중인 이체를 실행한다 */
    CONFIRM,

    /** 취소 발화. 대기 중인 이체를 폐기한다 */
    CANCEL,

    /** 판별 실패 */
    UNKNOWN,

    /** 예약값 — MVP Voice API는 반환하지 않는다 */
    GUARDIAN,

    /** 예약값 — MVP Voice API는 반환하지 않는다 */
    SETTING
}
