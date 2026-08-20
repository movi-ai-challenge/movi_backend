package com.movi_backend.domain.transfer.infrastructure;

import com.movi_backend.domain.transfer.config.OpenBankingProperties;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 Sandbox 승인 전까지 쓰는 대역.
 *
 * <p>실제 송금은 일어나지 않는다. 잔액은 설정값을 그대로 돌려준다.
 *
 * <p>로그에 계좌번호를 남기지 않는다. Mock이라고 예외를 두면 습관이 실제 구현으로 넘어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movi.openbanking", name = "mode", havingValue = OpenBankingProperties.MODE_MOCK, matchIfMissing = true)
public class MockOpenBankingClient implements OpenBankingClient {

    private static final String MOCK_TRANSACTION_ID_PREFIX = "mock-tran-";

    private final OpenBankingProperties openBankingProperties;

    @Override
    public long inquireBalance(final String fintechUseNum) {
        return openBankingProperties.mockBalance();
    }

    @Override
    public OpenBankingTransferResult transfer(final OpenBankingTransferCommand command) {
        log.info("[MOCK 오픈뱅킹] 이체 실행 transferId={} bankCode={} amount={}",
                command.transferId(), command.toBankCode(), command.amount());
        return OpenBankingTransferResult.success(MOCK_TRANSACTION_ID_PREFIX + UUID.randomUUID());
    }
}
