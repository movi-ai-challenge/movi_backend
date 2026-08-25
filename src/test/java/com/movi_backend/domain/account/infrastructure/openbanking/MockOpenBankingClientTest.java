package com.movi_backend.domain.account.infrastructure.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockOpenBankingClientTest {

    private static final String PRIMARY = "199000000000000000000001";
    private static final String TOKEN = "mock-access-token";

    @Test
    @DisplayName("계좌 목록을 조회하면 연결된 계좌를 반환한다")
    void 계좌_목록을_반환한다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();

        // when
        final List<OpenBankingAccount> accounts = client.fetchAccounts("U001", "mock-token");

        // then
        assertThat(accounts).isNotEmpty();
        assertThat(accounts).extracting(OpenBankingAccount::fintechUseNum).contains(PRIMARY);
    }

    @Test
    @DisplayName("이체하면 그만큼 잔액이 줄어든다")
    void 이체하면_잔액이_차감된다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();
        final long before = client.currentBalanceOf(PRIMARY);

        // when
        client.transfer(command("tran-1", 50_000L), TOKEN);

        // then
        assertThat(client.currentBalanceOf(PRIMARY)).isEqualTo(before - 50_000L);
    }

    @Test
    @DisplayName("같은 거래 키로 다시 이체하면 새로 실행하지 않고 기존 결과를 반환한다")
    void 같은_거래_키는_한_번만_실행한다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();
        final long before = client.currentBalanceOf(PRIMARY);

        // when
        final OpenBankingTransferResult first = client.transfer(command("tran-dup", 10_000L), TOKEN);
        final OpenBankingTransferResult second = client.transfer(command("tran-dup", 10_000L), TOKEN);

        // then
        assertThat(second.bankTranId()).isEqualTo(first.bankTranId());
        assertThat(client.currentBalanceOf(PRIMARY)).isEqualTo(before - 10_000L);
    }

    @Test
    @DisplayName("잔액보다 큰 금액을 이체하면 예외가 발생한다")
    void 잔액이_부족하면_거부한다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();
        final long balance = client.currentBalanceOf(PRIMARY);

        // when & then
        assertThatThrownBy(() -> client.transfer(command("tran-over", balance + 1L), TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("잔액이 부족해 거부되면 잔액이 그대로 유지된다")
    void 거부된_이체는_잔액을_바꾸지_않는다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();
        final long before = client.currentBalanceOf(PRIMARY);

        // when
        assertThatThrownBy(() -> client.transfer(command("tran-over", before + 1L), TOKEN))
                .isInstanceOf(BusinessException.class);

        // then
        assertThat(client.currentBalanceOf(PRIMARY)).isEqualTo(before);
    }

    @Test
    @DisplayName("등록되지 않은 핀테크이용번호를 쓰면 예외가 발생한다")
    void 알_수_없는_계좌는_거부한다() {
        // given
        final MockOpenBankingClient client = new MockOpenBankingClient();

        // when & then
        assertThatThrownBy(() -> client.currentBalanceOf("999999999999999999999999"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FINTECH_USE_NUM);
    }

    private OpenBankingTransferCommand command(final String tranId, final Long amount) {
        return OpenBankingTransferCommand.of(tranId, PRIMARY, "088", "110-123-456789", "김영희", amount);
    }
}
