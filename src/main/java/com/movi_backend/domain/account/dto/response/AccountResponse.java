package com.movi_backend.domain.account.dto.response;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.type.AccountType;

/**
 * 연결 계좌 1건.
 *
 * <p>계좌번호는 마스킹된 값만 내려보낸다. 원문은 서버 밖으로 나가지 않는다.
 */
public record AccountResponse(
        Long accountId,
        String bankName,
        String accountNumMasked,
        String accountAlias,
        AccountType accountType,
        boolean primary
) {
    public static AccountResponse from(final Account account) {
        return new AccountResponse(
                account.getId(),
                account.getBankName(),
                account.getAccountNumMasked(),
                account.getAlias(),
                account.getAccountType(),
                account.isPrimary()
        );
    }
}
