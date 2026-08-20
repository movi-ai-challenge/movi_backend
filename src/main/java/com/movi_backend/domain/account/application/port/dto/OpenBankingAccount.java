package com.movi_backend.domain.account.application.port.dto;

import com.movi_backend.domain.account.type.AccountType;

/**
 * 오픈뱅킹이 알려주는 계좌 1건.
 *
 * <p>{@code fintechUseNum}이 계좌의 실질 식별자다. 계좌번호 원문은 받지 않고
 * 마스킹된 값만 다룬다.
 *
 * @param fintechUseNum    핀테크이용번호
 * @param bankCode         은행 코드
 * @param bankName         은행명
 * @param accountNumMasked 마스킹된 계좌번호
 * @param holderName       예금주명
 * @param accountType      계좌 종류
 */
public record OpenBankingAccount(
        String fintechUseNum,
        String bankCode,
        String bankName,
        String accountNumMasked,
        String holderName,
        AccountType accountType
) {
    public static OpenBankingAccount of(
            final String fintechUseNum,
            final String bankCode,
            final String bankName,
            final String accountNumMasked,
            final String holderName,
            final AccountType accountType
    ) {
        return new OpenBankingAccount(
                fintechUseNum, bankCode, bankName, accountNumMasked, holderName, accountType);
    }
}
