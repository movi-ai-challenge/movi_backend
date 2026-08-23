package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockBalanceInquiryAdapterTest {

    private static final String FINTECH_USE_NUM = "199000000000000000000001";
    private static final String ACCESS_TOKEN = "mock-access-token";

    @Test
    @DisplayName("Mock 이체 후 잔액을 조회하면 차감된 잔액을 반환한다")
    void Mock_이체_후_잔액을_조회하면_차감된_잔액을_반환한다() {
        // given
        final MockOpenBankingClient openBankingClient = new MockOpenBankingClient();
        final MockBalanceInquiryAdapter adapter =
                new MockBalanceInquiryAdapter(openBankingClient);
        final long initialBalance = openBankingClient.currentBalanceOf(FINTECH_USE_NUM);
        openBankingClient.transfer(
                OpenBankingTransferCommand.of(
                        "shared-balance-test",
                        FINTECH_USE_NUM,
                        "088",
                        "110-123-456789",
                        "김영희",
                        50_000L
                ),
                ACCESS_TOKEN
        );

        // when
        final BalanceInquiryResult result = adapter.inquire(FINTECH_USE_NUM, ACCESS_TOKEN);

        // then
        assertThat(result.balanceAmount()).isEqualTo(initialBalance - 50_000L);
        assertThat(result.availableAmount()).isEqualTo(initialBalance - 50_000L);
    }
}
