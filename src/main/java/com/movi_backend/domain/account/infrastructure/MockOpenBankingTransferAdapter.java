package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.OpenBankingTransferPort;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 출금 없이 이체를 끝낸 것처럼 처리하는 대역.
 *
 * <p>오픈뱅킹 출금이체 API는 사업자 등록을 마친 이용기관에만 열린다. 계좌 연결(인증)까지는
 * 샌드박스로 진행할 수 있어도 이체는 보낼 수 없어, 그 구간만 이 대역으로 대체한다.
 *
 * <p>Mock 오픈뱅킹으로 만든 계좌는 그쪽이 잔액과 멱등성을 이미 들고 있으므로 그대로 넘긴다.
 * Mock이 모르는 계좌(실제로 연결된 계좌)는 {@link MockTransferLedger}가 대신 들고 간다.
 * 어느 쪽이든 <b>이체하면 잔액이 줄어든다</b> — 보내고 나서 잔액을 다시 묻는 흐름이 실제처럼
 * 이어져야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.transfer-mode", havingValue = "mock", matchIfMissing = true)
public class MockOpenBankingTransferAdapter implements OpenBankingTransferPort {

    private final ObjectProvider<MockOpenBankingClient> mockOpenBankingClient;
    private final MockTransferLedger mockTransferLedger;

    @Override
    public OpenBankingTransferResult transfer(
            final OpenBankingTransferCommand command,
            final String accessToken
    ) {
        final MockOpenBankingClient client = mockOpenBankingClient.getIfAvailable();
        if (client != null && client.knows(command.fromFintechUseNum())) {
            return client.transfer(command, accessToken);
        }
        return mockTransferLedger.transfer(command);
    }
}
