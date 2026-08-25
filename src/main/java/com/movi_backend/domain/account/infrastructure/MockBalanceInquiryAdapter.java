package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 잔액조회 Mock 어댑터.
 *
 * <p>실제 잔액조회는 금융 사업자만 호출할 수 있어, 계좌 연결·목록을 실 API 로 쓰면서도
 * 잔액만 이 어댑터로 두는 조합이 필요하다. 그래서 {@code movi.openbanking.mode} 가 아니라
 * {@code movi.openbanking.balance-mode} 로 갈린다. 값을 적지 않으면 이 어댑터가 선택된다 —
 * 실 API 는 우리 자격으로 응답을 받지 못하므로 안전한 쪽이 기본이어야 한다.
 *
 * <p>잔액을 구하는 방법이 두 가지다.
 *
 * <ol>
 *   <li>전부 mock 인 환경 — {@link MockOpenBankingClient} 가 들고 있는 잔액을 그대로 쓴다.
 *       이체하면 그만큼 줄어드는 흐름이 유지된다</li>
 *   <li>계좌만 실 연동인 환경 — 실 계좌의 핀테크이용번호는 Mock 어댑터가 모른다.
 *       이때는 {@link #FALLBACK_BALANCE} 를 돌려준다</li>
 * </ol>
 *
 * <p>2번에서 계좌마다 다른 값을 만들지 않고 고정값을 쓴다. 화면 없이 음성으로 시연하는
 * 제품이라 <b>안내 문구가 매번 같아야</b> 시나리오를 짤 수 있기 때문이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.balance-mode", havingValue = "mock", matchIfMissing = true)
public class MockBalanceInquiryAdapter implements BalanceInquiryPort {

    /** Mock 어댑터가 모르는 계좌에 돌려줄 잔액. 5만 3천원이 아니라 53만원이다. */
    private static final long FALLBACK_BALANCE = 530_000L;

    private final ObjectProvider<MockOpenBankingClient> mockOpenBankingClient;

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        final long balance = balanceOf(fintechUseNum);
        return BalanceInquiryResult.of(balance, balance);
    }

    private long balanceOf(final String fintechUseNum) {
        final MockOpenBankingClient client = mockOpenBankingClient.getIfAvailable();
        if (client == null) {
            return FALLBACK_BALANCE;
        }
        if (!client.knows(fintechUseNum)) {
            return FALLBACK_BALANCE;
        }
        return client.currentBalanceOf(fintechUseNum);
    }
}
