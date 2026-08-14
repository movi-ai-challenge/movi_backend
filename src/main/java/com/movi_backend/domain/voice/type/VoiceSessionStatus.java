package com.movi_backend.domain.voice.type;

/**
 * 음성 세션 상태. docs/integration-spec.md 6.1절을 따른다.
 *
 * <pre>
 * ACTIVE
 * ├─ CLARIFYING
 * ├─ AWAITING_CONFIRMATION
 * │  ├─ PROCESSING → COMPLETED
 * │  └─ CANCELED
 * └─ EXPIRED
 * </pre>
 */
public enum VoiceSessionStatus {

    /** 명령 대기 */
    ACTIVE,

    /** 필수 슬롯이 비어 재질문 중 */
    CLARIFYING,

    /** 확인 문장을 읽어 주고 사용자 응답을 기다리는 중 */
    AWAITING_CONFIRMATION,

    /** 확인을 받아 이체를 처리하는 중. 이 상태에서는 확인 발화를 다시 받지 않는다 */
    PROCESSING,

    /** 처리 완료 */
    COMPLETED,

    /** 사용자가 취소 */
    CANCELED,

    /** 유효시간 초과 */
    EXPIRED;

    /** 더 이상 발화를 받지 않는 상태인지 여부 */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELED || this == EXPIRED;
    }

    /** 확인 발화를 중복 수신하면 안 되는 상태인지 여부 */
    public boolean isProcessing() {
        return this == PROCESSING;
    }
}
