package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "mock", matchIfMissing = true)
public class MockBalanceInquiryAdapter implements BalanceInquiryPort {

    private static final long MOCK_BALANCE_AMOUNT = 53_000L;
    private static final long MOCK_AVAILABLE_AMOUNT = 53_000L;

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        return BalanceInquiryResult.of(MOCK_BALANCE_AMOUNT, MOCK_AVAILABLE_AMOUNT);
    }
}
