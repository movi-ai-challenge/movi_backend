package com.movi_backend.domain.account.application.port;

public record BalanceInquiryResult(
        long balanceAmount,
        long availableAmount
) {

    public static BalanceInquiryResult of(final long balanceAmount, final long availableAmount) {
        return new BalanceInquiryResult(balanceAmount, availableAmount);
    }

    public boolean isValid() {
        if (this.balanceAmount < 0L || this.availableAmount < 0L) {
            return false;
        }
        return this.availableAmount <= this.balanceAmount;
    }
}
