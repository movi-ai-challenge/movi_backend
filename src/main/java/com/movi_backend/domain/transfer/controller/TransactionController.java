package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.TransactionQueryService;
import com.movi_backend.domain.transfer.controller.docs.TransactionApiDocs;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController implements TransactionApiDocs {

    private final TransactionQueryService transactionQueryService;

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> getTransactions(
            @CurrentUser final AuthUser authUser,
            @RequestParam(required = false) final Long accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate,
            @RequestParam(required = false) final TransactionType type,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        return ApiResponse.success(transactionQueryService.findAll(
                authUser.userId(),
                accountId,
                startDate,
                endDate,
                type,
                page,
                size
        ));
    }
}
