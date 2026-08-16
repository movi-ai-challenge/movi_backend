package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class MockBalanceInquiryAdapter implements BalanceInquiryPort {

    private static final long MOCK_BALANCE_AMOUNT = 53_000L;
    private static final long MOCK_AVAILABLE_AMOUNT = 53_000L;

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        return BalanceInquiryResult.of(MOCK_BALANCE_AMOUNT, MOCK_AVAILABLE_AMOUNT);
    }
}
