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
    private static final String ANOTHER_REAL_FINTECH_USE_NUM = "120260215288981369293168";
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
    @DisplayName("Mock이 모르는 실 계좌도 잔액을 돌려주고, 같은 계좌면 매번 같은 금액이다")
    void 모르는_실_계좌도_잔액을_돌려주고_매번_같은_금액이다() {
        // given — 계좌만 실 연동한 환경. 실 핀테크이용번호는 Mock 어댑터에 없다.
        final MockBalanceInquiryAdapter adapter = adapterWith(new MockOpenBankingClient());

        // when
        final BalanceInquiryResult first = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN);
        final BalanceInquiryResult second = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN);

        // then — 물을 때마다 금액이 달라지면 이체 한도·잔액 검증이 통과했다 실패했다 한다.
        assertThat(first.balanceAmount()).isEqualTo(second.balanceAmount());
        assertThat(first.balanceAmount()).isPositive();
        assertThat(first.isValid()).isTrue();
    }

    @Test
    @DisplayName("계좌가 다르면 잔액도 다르게 만든다")
    void 계좌가_다르면_잔액도_다르게_만든다() {
        // given — 모든 계좌가 같은 금액이면 계좌를 골라 봐야 구분이 되지 않는다.
        final MockBalanceInquiryAdapter adapter = adapterWith(new MockOpenBankingClient());

        // when
        final long first = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN).balanceAmount();
        final long second = adapter.inquire(ANOTHER_REAL_FINTECH_USE_NUM, ACCESS_TOKEN).balanceAmount();

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("만들어 낸 잔액은 만원 단위라 음성으로 읽기 좋다")
    void 만들어_낸_잔액은_만원_단위다() {
        final MockBalanceInquiryAdapter adapter = adapterWith(new MockOpenBankingClient());

        final long balance = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN).balanceAmount();

        assertThat(balance % 10_000L).isZero();
    }

    @Test
    @DisplayName("Mock 오픈뱅킹 어댑터가 아예 없어도 잔액을 돌려준다")
    void Mock_어댑터가_없어도_잔액을_돌려준다() {
        // given — mode=real 이면 MockOpenBankingClient 빈 자체가 만들어지지 않는다.
        final MockBalanceInquiryAdapter adapter = adapterWith(null);

        // when
        final BalanceInquiryResult result = adapter.inquire(REAL_FINTECH_USE_NUM, ACCESS_TOKEN);

        // then
        assertThat(result.balanceAmount()).isPositive();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("핀테크이용번호가 비어 있으면 고정 잔액으로 물러난다")
    void 핀테크이용번호가_비어_있으면_고정_잔액으로_물러난다() {
        final MockBalanceInquiryAdapter adapter = adapterWith(new MockOpenBankingClient());

        assertThat(adapter.inquire(null, ACCESS_TOKEN).balanceAmount()).isEqualTo(FALLBACK_BALANCE);
        assertThat(adapter.inquire("   ", ACCESS_TOKEN).balanceAmount()).isEqualTo(FALLBACK_BALANCE);
    }

    private MockBalanceInquiryAdapter adapterWith(final MockOpenBankingClient client) {
        return new MockBalanceInquiryAdapter(new ObjectProvider<MockOpenBankingClient>() {
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
        }, new MockTransferLedger());
    }
}
