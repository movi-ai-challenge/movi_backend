package com.movi_backend.domain.transfer.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferTest {

    @Test
    @DisplayName("이체를 생성하면 대기 상태로 시작한다")
    void 이체를_생성하면_대기_상태로_시작한다() {
        // given & when
        final Transfer transfer = createTransfer();

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    @DisplayName("위험도 평가를 시작하면 검토 상태로 변경한다")
    void 위험도_평가를_시작하면_검토_상태로_변경한다() {
        // given
        final Transfer transfer = createTransfer();

        // when
        transfer.startRiskReview();

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RISK_REVIEW);
    }

    @Test
    @DisplayName("위험도 평가를 거친 이체를 완료하면 완료 시각을 기록한다")
    void 위험도_평가를_거친_이체를_완료하면_완료_시각을_기록한다() {
        // given
        final Transfer transfer = createTransfer();
        final LocalDateTime completedAt = LocalDateTime.of(2026, 8, 12, 12, 0);
        transfer.startRiskReview();

        // when
        transfer.complete(completedAt);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("위험도 평가 없이 이체를 완료하면 예외가 발생한다")
    void 위험도_평가_없이_이체를_완료하면_예외가_발생한다() {
        // given
        final Transfer transfer = createTransfer();

        // when & then
        assertThatThrownBy(() -> transfer.complete(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    @DisplayName("고위험 이체를 차단하면 차단 상태와 사유를 기록한다")
    void 고위험_이체를_차단하면_차단_상태와_사유를_기록한다() {
        // given
        final Transfer transfer = createTransfer();
        final String reason = "고위험 거래";
        transfer.startRiskReview();

        // when
        transfer.block(reason);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        assertThat(transfer.getFailReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("완료된 이체의 상태를 변경하면 예외가 발생한다")
    void 완료된_이체의_상태를_변경하면_예외가_발생한다() {
        // given
        final Transfer transfer = createTransfer();
        transfer.startRiskReview();
        transfer.complete(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> transfer.block("고위험 거래"))
                .isInstanceOf(BusinessException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    private Transfer createTransfer() {
        return Transfer.builder()
                .toBankCode("004")
                .toAccountNum("encrypted-account-number")
                .toHolderName("홍길동")
                .amount(50_000L)
                .idempotencyKey("transfer-state-test-key")
                .build();
    }
}
