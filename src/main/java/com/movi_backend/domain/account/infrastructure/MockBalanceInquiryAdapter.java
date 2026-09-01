package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 잔액을 오픈뱅킹 대신 로컬에서 만들어 주는 대역.
 *
 * <p>오픈뱅킹 잔액조회 API는 사업자 등록을 마친 이용기관에만 열린다. 계좌 연결(인증)까지는
 * 샌드박스로 진행할 수 있어도 잔액은 받아올 수 없어, 그 구간만 이 대역으로 대체한다.
 * 그래서 {@code movi.openbanking.mode}(연결)와 {@code movi.openbanking.balance-mode}(잔액)를
 * 따로 둔다. 연결은 real, 잔액은 mock 조합이 가능하다.
 *
 * <p>Mock 오픈뱅킹으로 만든 계좌는 그쪽이 들고 있는 잔액을 그대로 읽는다. 이체하면 줄어들기
 * 때문에 "보내고 나서 잔액을 다시 물어보는" 흐름이 실제처럼 이어진다.
 *
 * <p>실제로 연결된 계좌처럼 Mock이 모르는 계좌는 {@link MockTransferLedger}가 잔액을 들고 간다.
 * 대역 이체도 이 장부를 깎으므로 보내고 나면 잔액이 줄어든다. 금액은 핀테크이용번호에서 만든다.
 * <b>계좌마다 다르고, 같은 계좌면 언제 물어도 같은 금액이어야 한다.</b> 모두 같은 금액이면
 * 계좌를 골라 봐야 구분이 안 되고, 물을 때마다 달라지면 이체 한도·잔액 검증이 통과했다
 * 실패했다 한다. {@code String.hashCode()}는 명세에 값이 고정돼 있어 실행·장비가 달라져도
 * 같은 금액이 나온다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.balance-mode", havingValue = "mock", matchIfMissing = true)
public class MockBalanceInquiryAdapter implements BalanceInquiryPort {

    private final ObjectProvider<MockOpenBankingClient> mockOpenBankingClient;
    private final MockTransferLedger mockTransferLedger;

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        final long balance = balanceOf(fintechUseNum);
        return BalanceInquiryResult.of(balance, balance);
    }

    private long balanceOf(final String fintechUseNum) {
        // knows()가 들여다보는 ConcurrentHashMap 은 null 키에 NPE 를 던진다. 먼저 걸러낸다.
        if (fintechUseNum == null || fintechUseNum.isBlank()) {
            return mockTransferLedger.balanceOf(fintechUseNum);
        }

        final MockOpenBankingClient client = mockOpenBankingClient.getIfAvailable();
        if (client != null && client.knows(fintechUseNum)) {
            return client.currentBalanceOf(fintechUseNum);
        }
        return mockTransferLedger.balanceOf(fintechUseNum);
    }
}
