package com.movi_backend.domain.account.application.port;

public interface BalanceInquiryPort {

    BalanceInquiryResult inquire(String fintechUseNum, String accessToken);
}
