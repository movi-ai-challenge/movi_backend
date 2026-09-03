package com.movi_backend.domain.voice.type;

import java.util.Set;

/**
 * 음성 세션 상태. docs/integration-spec.md 6.1절을 따른다.
 *
 * <pre>
 * ACTIVE
 * ├─ CLARIFYING ─ (의도 전환) → ACTIVE
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

    /**
     * 같은 슬롯을 반복해서 물을 수 있으므로 자기 자신으로의 전이를 허용한다.
     *
     * <p>{@code ACTIVE}로 되돌아가는 전이는 <b>의도 전환</b>용이다. 송금 슬롯을 채우는 중에
     * 사용자가 거래내역을 물으면 앞선 송금은 포기된 것으로 보고 슬롯을 폐기한 뒤 명령 대기로
     * 돌아간다. 남겨 두면 뒤이은 발화가 옛 슬롯과 병합돼 엉뚱한 이체로 이어진다.
     */
    private static final Set<VoiceSessionStatus> FROM_CLARIFYING =
            Set.of(ACTIVE, CLARIFYING, AWAITING_CONFIRMATION, COMPLETED, CANCELED, EXPIRED);

    /**
     * 확인을 기다리는 중에도 {@code ACTIVE}로 되돌아갈 수 있다. <b>의도 전환</b>이다.
     *
     * <p>"보낼까요?"에 "네"가 아니라 새 명령이 오는 경우가 있다 -- 화면을 보지 않는
     * 사용자는 안내를 못 들었으면 같은 말을 다시 할 수밖에 없다. 이때 오류로 막으면
     * 반복할수록 계속 막힌다. 앞선 확인은 포기된 것으로 보고 슬롯을 폐기한 뒤 새 명령으로
     * 받는다. 돈이 나가지는 않는다 -- 새 명령은 다시 확인 질문으로 이어질 뿐이다.
     */
    private static final Set<VoiceSessionStatus> FROM_AWAITING_CONFIRMATION =
            Set.of(ACTIVE, PROCESSING, CLARIFYING, CANCELED, EXPIRED);

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
