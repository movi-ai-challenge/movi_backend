package com.movi_backend.domain.account.controller;

import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceInquiryService balanceInquiryService;

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
}
