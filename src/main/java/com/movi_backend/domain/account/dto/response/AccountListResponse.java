package com.movi_backend.domain.account.dto.response;

import com.movi_backend.domain.account.entity.Account;
import java.util.List;

/**
 * 연결 계좌 목록.
 *
 * <p>{@code voiceMessage}로 읽어 줄 문구를 함께 만든다. 화면을 보지 못하는 사용자에게는
 * 목록을 눈으로 훑는 대신 "몇 개가 연결돼 있고 어느 것이 기본인지"를 들려줘야 한다.
 */
public record AccountListResponse(
        int totalCount,
        List<AccountResponse> accounts
) {
    public static AccountListResponse from(final List<Account> accounts) {
        return new AccountListResponse(
                accounts.size(),
                accounts.stream().map(AccountResponse::from).toList()
        );
    }

    public String toVoiceMessage() {
        if (this.accounts.isEmpty()) {
            return "연결된 계좌가 없어요. 계좌를 먼저 연결해 주세요.";
        }
        return "계좌가 %d개 연결되어 있어요. %s".formatted(this.totalCount, describePrimary());
    }

    private String describePrimary() {
        return this.accounts.stream()
                .filter(AccountResponse::primary)
                .findFirst()
                .map(account -> "주로 쓰는 계좌는 %s이에요.".formatted(displayName(account)))
                .orElse("주로 쓰실 계좌를 정해 주세요.");
    }

    private String displayName(final AccountResponse account) {
        if (account.accountAlias() == null || account.accountAlias().isBlank()) {
            return account.bankName() + " 계좌";
        }
        return account.bankName() + " " + account.accountAlias();
    }
}
