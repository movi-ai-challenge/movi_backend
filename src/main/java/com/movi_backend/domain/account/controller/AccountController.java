package com.movi_backend.domain.account.controller;

import com.movi_backend.domain.account.application.AccountService;
import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.request.AccountAliasChangeRequest;
import com.movi_backend.domain.account.dto.response.AccountListResponse;
import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceInquiryService balanceInquiryService;
    private final AccountService accountService;

    @GetMapping("/balance")
    public ApiResponse<BalanceResponse> getBalance(
            @CurrentUser final AuthUser authUser,
            @RequestParam(required = false) final String accountAlias
    ) {
        final BalanceResponse balance = balanceInquiryService.inquire(
                authUser.userId(),
                accountAlias
        );
        return ApiResponse.success(balance, balance.toVoiceMessage());
    }

    /** 연결된 계좌 목록 (명세서 1.3) */
    @GetMapping
    public ApiResponse<AccountListResponse> getAccounts(@CurrentUser final AuthUser authUser) {
        final AccountListResponse accounts = accountService.findAll(authUser.userId());
        return ApiResponse.success(accounts, accounts.toVoiceMessage());
    }

    /** 기본 계좌 지정 (명세서 1.4) */
    @PatchMapping("/{accountId}/primary")
    public ApiResponse<AccountResponse> designatePrimary(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long accountId
    ) {
        final AccountResponse account = accountService.designatePrimary(authUser.userId(), accountId);
        return ApiResponse.success(account, "주로 쓰는 계좌로 정했어요.");
    }

    /** 계좌 별칭 변경 */
    @PatchMapping("/{accountId}/alias")
    public ApiResponse<AccountResponse> changeAlias(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long accountId,
            @Valid @RequestBody final AccountAliasChangeRequest request
    ) {
        final AccountResponse account = accountService.changeAlias(
                authUser.userId(),
                accountId,
                request.alias()
        );
        return ApiResponse.success(account, "계좌 별칭을 바꿨어요.");
    }
}
