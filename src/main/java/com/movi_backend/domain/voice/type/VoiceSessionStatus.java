package com.movi_backend.domain.voice.type;

import java.util.Set;

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
 *
 * <p>전이 규칙은 {@link #canTransitionTo}가 강제한다. 종료 상태에서는 어떤 전이도 허용하지 않는다.
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

    private static final Set<VoiceSessionStatus> FROM_ACTIVE =
            Set.of(CLARIFYING, AWAITING_CONFIRMATION, COMPLETED, CANCELED, EXPIRED);

    /** 같은 슬롯을 반복해서 물을 수 있으므로 자기 자신으로의 전이를 허용한다 */
    private static final Set<VoiceSessionStatus> FROM_CLARIFYING =
            Set.of(CLARIFYING, AWAITING_CONFIRMATION, COMPLETED, CANCELED, EXPIRED);

    private static final Set<VoiceSessionStatus> FROM_AWAITING_CONFIRMATION =
            Set.of(PROCESSING, CLARIFYING, CANCELED, EXPIRED);

    private static final Set<VoiceSessionStatus> FROM_PROCESSING =
            Set.of(COMPLETED, CANCELED, EXPIRED);

    /** 더 이상 발화를 받지 않는 상태인지 여부 */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELED || this == EXPIRED;
    }

    /** 확인 발화를 중복 수신하면 안 되는 상태인지 여부 */
    public boolean isProcessing() {
        return this == PROCESSING;
    }

    /** {@code next}로 전이할 수 있는지 여부. 종료 상태에서는 항상 false다. */
    public boolean canTransitionTo(final VoiceSessionStatus next) {
        if (this == ACTIVE) {
            return FROM_ACTIVE.contains(next);
        }
        if (this == CLARIFYING) {
            return FROM_CLARIFYING.contains(next);
        }
        if (this == AWAITING_CONFIRMATION) {
            return FROM_AWAITING_CONFIRMATION.contains(next);
        }
        if (this == PROCESSING) {
            return FROM_PROCESSING.contains(next);
        }
        return false;
    }
}
