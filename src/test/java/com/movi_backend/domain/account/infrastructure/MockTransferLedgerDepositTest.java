package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockTransferLedgerDepositTest {

    private static final String SENDER = "sender-fintech-num";
    private static final String RECEIVER = "receiver-fintech-num";

    @Test
    @DisplayName("입금하면 받는 계좌 잔액이 늘어난다")
    void 입금하면_받는_계좌_잔액이_늘어난다() {
        // given
        final MockTransferLedger ledger = new MockTransferLedger();
        final long before = ledger.balanceOf(RECEIVER);

        // when
        ledger.deposit("tran-1", RECEIVER, 10_000L);

        // then
        assertThat(ledger.balanceOf(RECEIVER)).isEqualTo(before + 10_000L);
    }

    @Test
    @DisplayName("같은 거래로 다시 입금하지 않는다 - 재시도에 두 번 들어가면 없는 돈이 생긴다")
    void 같은_거래로_다시_입금하지_않는다() {
        // given
        final MockTransferLedger ledger = new MockTransferLedger();
        final long before = ledger.balanceOf(RECEIVER);

        // when
        ledger.deposit("tran-1", RECEIVER, 10_000L);
        ledger.deposit("tran-1", RECEIVER, 10_000L);

        // then
        assertThat(ledger.balanceOf(RECEIVER)).isEqualTo(before + 10_000L);
    }

    @Test
    @DisplayName("출금과 입금이 서로의 멱등 기록을 가로막지 않는다")
    void 출금과_입금이_서로를_가로막지_않는다() {
        // given - 같은 tranId 로 출금과 입금이 각각 한 번씩 일어난다.
        final MockTransferLedger ledger = new MockTransferLedger();
        final long senderBefore = ledger.balanceOf(SENDER);
        final long receiverBefore = ledger.balanceOf(RECEIVER);

        // when
        ledger.transfer(com.movi_backend.domain.account.application.port.dto
                .OpenBankingTransferCommand.of(
                        "tran-1", SENDER, "012", "3522315749001", "주혁", 10_000L));
        ledger.deposit("tran-1", RECEIVER, 10_000L);

        // then - 출금 기록이 있다고 입금이 건너뛰어지면 안 된다.
        assertThat(ledger.balanceOf(SENDER)).isEqualTo(senderBefore - 10_000L);
        assertThat(ledger.balanceOf(RECEIVER)).isEqualTo(receiverBefore + 10_000L);
    }
}
