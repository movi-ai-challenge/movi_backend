package com.movi_backend.domain.voice.type;

/**
 * 음성 명령 처리 결과. CLARIFY는 필수 슬롯이 비어 재질문한 경우다.
 */
public enum VoiceCommandStatus {

    /** 정상 처리 */
    SUCCESS,
    /** 재질문 */
    CLARIFY,
    /** 처리 실패 */
    FAILED
}
