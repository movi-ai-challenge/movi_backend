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
 *
 * <p><b>받는 쪽이 우리 사용자면 그 잔액도 늘려 준다.</b> 실제 오픈뱅킹이라면 상대 은행이
 * 입금하지만 대역에는 그 상대가 없다. 두 사람이 각자 폰으로 보는 시연에서 한쪽만 줄고 다른
 * 쪽이 그대로면 돈이 사라진 것처럼 보인다.
 *
 * <p>입금은 <b>이체가 끝난 뒤 덤으로 하는 일</b>이다. 받는 계좌를 못 찾거나 입금이 실패해도
 * 이체 자체는 이미 성공했으므로 되돌리지 않는다 — 여기서 예외를 올리면 정상적으로 나간 돈이
 * 실패로 보고된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.transfer-mode", havingValue = "mock", matchIfMissing = true)
public class MockOpenBankingTransferAdapter implements OpenBankingTransferPort {

    private final ObjectProvider<MockOpenBankingClient> mockOpenBankingClient;
    private final MockTransferLedger mockTransferLedger;
    private final MockDepositAccountResolver depositAccountResolver;

    @Override
    public OpenBankingTransferResult transfer(
            final OpenBankingTransferCommand command,
            final String accessToken
    ) {
        final OpenBankingTransferResult result = withdraw(command, accessToken);
        depositToReceiver(command);
        return result;
    }

    private OpenBankingTransferResult withdraw(
            final OpenBankingTransferCommand command,
            final String accessToken
    ) {
        final MockOpenBankingClient client = mockOpenBankingClient.getIfAvailable();
        if (client != null && client.knows(command.fromFintechUseNum())) {
            return client.transfer(command, accessToken);
        }
        return mockTransferLedger.transfer(command);
    }

    /** 받는 사람이 우리 사용자일 때만 넣는다. 아니면 조용히 지나간다. */
    private void depositToReceiver(final OpenBankingTransferCommand command) {
        try {
            depositAccountResolver
                    .resolveFintechUseNum(command.toBankCode(), command.toAccountNum())
                    .ifPresent(toFintechUseNum -> mockTransferLedger.deposit(
                            command.tranId(),
                            toFintechUseNum,
                            command.amount()
                    ));
        } catch (final RuntimeException exception) {
            log.warn(
                    "[MOCK] 받는 쪽 입금에 실패했습니다. 이체는 이미 끝났습니다. tranId={} 원인={}",
                    command.tranId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
