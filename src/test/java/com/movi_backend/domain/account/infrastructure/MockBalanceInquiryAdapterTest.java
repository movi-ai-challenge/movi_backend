package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MockBalanceInquiryAdapterTest {

    private static final String FINTECH_USE_NUM = "199000000000000000000001";
    private static final String REAL_FINTECH_USE_NUM = "120260215288981369293167";
    private static final String ACCESS_TOKEN = "mock-access-token";
    private static final long FALLBACK_BALANCE = 530_000L;

    @Test
    @DisplayName("Mock 이체 후 잔액을 조회하면 차감된 잔액을 반환한다")
    void Mock_이체_후_잔액을_조회하면_차감된_잔액을_반환한다() {
        // given
        final MockOpenBankingClient openBankingClient = new MockOpenBankingClient();
        final MockBalanceInquiryAdapter adapter = adapterWith(openBankingClient);
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

    @Test
    @DisplayName("Mock 어댑터가 모르는 실 계좌도 잔액을 돌려준다")
    void 모르는_실_계좌도_잔액을_돌려준다() {
        // given — 계좌만 실 연동한 환경. 실 핀테크이용번호는 Mock 어댑터에 없다.
        final MockOpenBankingClient openBankingClient = new MockOpenBankingClient();
        final MockBalanceInquiryAdapter adapter = adapterWith(openBankingClient);

        // when
        final BalanceInquiryResult result = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN);

        // then
        assertThat(result.balanceAmount()).isEqualTo(FALLBACK_BALANCE);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Mock 오픈뱅킹 어댑터가 아예 없어도 잔액을 돌려준다")
    void Mock_어댑터가_없어도_잔액을_돌려준다() {
        // given — mode=real 이면 MockOpenBankingClient 빈 자체가 만들어지지 않는다.
        final MockBalanceInquiryAdapter adapter = adapterWith(null);

        // when
        final BalanceInquiryResult result = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN);

        // then
        assertThat(result.balanceAmount()).isEqualTo(FALLBACK_BALANCE);
    }

    private MockBalanceInquiryAdapter adapterWith(final MockOpenBankingClient client) {
        return new MockBalanceInquiryAdapter(new ObjectProvider<>() {
            @Override
            public MockOpenBankingClient getIfAvailable() {
                return client;
            }

            @Override
            public MockOpenBankingClient getObject() {
                return client;
            }

            @Override
            public MockOpenBankingClient getObject(final Object... args) {
                return client;
            }

            @Override
            public MockOpenBankingClient getIfUnique() {
                return client;
            }
        });
    }
}
