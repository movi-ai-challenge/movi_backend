package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "mock", matchIfMissing = true)
public class MockBalanceInquiryAdapter implements BalanceInquiryPort {

    private final MockOpenBankingClient openBankingClient;

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        final long balance = openBankingClient.currentBalanceOf(fintechUseNum);
        return BalanceInquiryResult.of(balance, balance);
    }
}
