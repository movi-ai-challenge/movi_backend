package com.movi_backend.domain.account.dto.response;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;

public record BalanceResponse(
        Long accountId,
        String bankName,
        String accountAlias,
        long balanceAmount,
        long availableAmount,
        LocalDateTime fetchedAt
) {

    public static BalanceResponse of(
            final Account account,
            final BalanceSnapshot balanceSnapshot
    ) {
        return new BalanceResponse(
                account.getId(),
                account.getBankName(),
                account.getAlias(),
                balanceSnapshot.getBalanceAmount(),
                balanceSnapshot.getAvailableAmount(),
                balanceSnapshot.getFetchedAt()
        );
    }

    public String toVoiceMessage() {
        final String accountName = createAccountName();
        final String balance = KoreanMoneyFormatter.format(this.balanceAmount);
        return "%s에 %s 있어요.".formatted(accountName, balance);
    }

    private String createAccountName() {
        if (this.accountAlias == null || this.accountAlias.isBlank()) {
            return this.bankName + " 계좌";
        }
        return this.bankName + " " + this.accountAlias;
    }
}
