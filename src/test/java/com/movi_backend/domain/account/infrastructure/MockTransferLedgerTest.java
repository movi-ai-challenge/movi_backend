package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockTransferLedgerTest {

    private static final String FINTECH_A = "120260215288981369293167";
    private static final String FINTECH_B = "120260215288981369293168";

    private final MockTransferLedger ledger = new MockTransferLedger();

    @Test
    @DisplayName("이체하면 잔액이 줄어든다")
    void 이체하면_잔액이_줄어든다() {
        final long before = ledger.balanceOf(FINTECH_A);

        final OpenBankingTransferResult result = ledger.transfer(command("tran-1", FINTECH_A, 50_000L));

        assertThat(ledger.balanceOf(FINTECH_A)).isEqualTo(before - 50_000L);
        assertThat(result.balanceAfter()).isEqualTo(before - 50_000L);
    }

    @Test
    @DisplayName("같은 거래 ID로 다시 요청하면 새로 깎지 않고 기존 결과를 돌려준다")
    void 같은_거래_ID로_다시_요청하면_새로_깎지_않는다() {
        final long before = ledger.balanceOf(FINTECH_A);
        final OpenBankingTransferResult first = ledger.transfer(command("tran-1", FINTECH_A, 50_000L));

        final OpenBankingTransferResult second = ledger.transfer(command("tran-1", FINTECH_A, 50_000L));

        assertThat(second.bankTranId()).isEqualTo(first.bankTranId());
        assertThat(ledger.balanceOf(FINTECH_A)).isEqualTo(before - 50_000L);
    }

    @Test
    @DisplayName("잔액이 모자라면 차감하지 않고 거부한다")
    void 잔액이_모자라면_차감하지_않고_거부한다() {
        final long before = ledger.balanceOf(FINTECH_A);

        assertThatThrownBy(() -> ledger.transfer(command("tran-1", FINTECH_A, before + 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(ledger.balanceOf(FINTECH_A)).isEqualTo(before);
    }

    @Test
    @DisplayName("계좌가 다르면 시작 잔액도 다르고, 같은 계좌면 매번 같다")
    void 계좌가_다르면_시작_잔액도_다르다() {
        assertThat(ledger.balanceOf(FINTECH_A)).isNotEqualTo(ledger.balanceOf(FINTECH_B));
        assertThat(ledger.balanceOf(FINTECH_A)).isEqualTo(ledger.balanceOf(FINTECH_A));
    }

    @Test
    @DisplayName("시작 잔액은 만원 단위라 음성으로 읽기 좋다")
    void 시작_잔액은_만원_단위다() {
        assertThat(ledger.balanceOf(FINTECH_A) % 10_000L).isZero();
    }

    @Test
    @DisplayName("핀테크이용번호가 비어 있으면 고정 잔액으로 물러난다")
    void 핀테크이용번호가_비어_있으면_고정_잔액으로_물러난다() {
        assertThat(ledger.balanceOf(null)).isEqualTo(530_000L);
        assertThat(ledger.balanceOf("   ")).isEqualTo(530_000L);
    }

    private OpenBankingTransferCommand command(
            final String tranId,
            final String fromFintechUseNum,
            final long amount
    ) {
        return OpenBankingTransferCommand.of(
                tranId, fromFintechUseNum, "088", "110-123-456789", "김영희", amount);
    }
}
