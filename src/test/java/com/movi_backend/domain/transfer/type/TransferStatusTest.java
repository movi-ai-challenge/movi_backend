package com.movi_backend.domain.transfer.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferStatusTest {

    @Test
    @DisplayName("고위험 판정은 평가 중 상태에서 확인 대기로 넘어간다")
    void 평가_중에서_확인_대기로_넘어간다() {
        // when & then
        assertThat(TransferStatus.RISK_REVIEW.canTransitionTo(TransferStatus.HOLD)).isTrue();
    }

    @Test
    @DisplayName("확인 대기에서 완료·차단으로 갈 수 있다")
    void 확인_대기에서_완료와_차단으로_갈_수_있다() {
        // when & then
        assertThat(TransferStatus.HOLD.canTransitionTo(TransferStatus.COMPLETED)).isTrue();
        assertThat(TransferStatus.HOLD.canTransitionTo(TransferStatus.BLOCKED)).isTrue();
        assertThat(TransferStatus.HOLD.canTransitionTo(TransferStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("평가를 건너뛰고 확인 대기로 갈 수 없다")
    void 평가를_건너뛸_수_없다() {
        // when & then
        assertThat(TransferStatus.PENDING.canTransitionTo(TransferStatus.HOLD)).isFalse();
        assertThat(TransferStatus.PENDING.canTransitionTo(TransferStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("확인 대기는 최종 상태가 아니고, 사용자 응답을 기다린다")
    void 확인_대기는_최종_상태가_아니다() {
        // when & then
        assertThat(TransferStatus.HOLD.isFinal()).isFalse();
        assertThat(TransferStatus.HOLD.awaitsConfirmation()).isTrue();
        assertThat(TransferStatus.RISK_REVIEW.awaitsConfirmation()).isFalse();
    }

    @Test
    @DisplayName("완료·차단 이후에는 어떤 상태로도 가지 않는다")
    void 최종_상태에서는_전이할_수_없다() {
        // when & then
        assertThat(TransferStatus.COMPLETED.canTransitionTo(TransferStatus.BLOCKED)).isFalse();
        assertThat(TransferStatus.BLOCKED.canTransitionTo(TransferStatus.COMPLETED)).isFalse();
        assertThat(TransferStatus.BLOCKED.canTransitionTo(TransferStatus.HOLD)).isFalse();
    }
}
