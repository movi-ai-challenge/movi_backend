package com.movi_backend.domain.voice.type;

/**
 * 음성 명령 의도. AI 파트가 추출한 값을 그대로 받는다.
 */
public enum VoiceIntent {

    /** 잔액 조회 */
    BALANCE,
    /** 이체 */
    TRANSFER,
    /** 거래내역 조회 */
    HISTORY,
    /** 보호자 */
    GUARDIAN,
    /** 설정 */
    SETTING,
    /** 판별 실패 */
    UNKNOWN
}
