package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.TransactionQueryService;
import com.movi_backend.domain.transfer.dto.response.TransactionDetailResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final String EMPTY_LIST_VOICE_MESSAGE = "그 기간에는 거래 내역이 없어요.";

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
        final PageResponse<TransactionResponse> transactions = transactionQueryService.findAll(
                authUser.userId(),
                accountId,
                startDate,
                endDate,
                type,
                page,
                size
        );
        return ApiResponse.success(transactions, toListVoiceMessage(transactions));
    }

    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionDetailResponse> getTransaction(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long transactionId
    ) {
        final TransactionDetailResponse transaction = transactionQueryService.findOne(
                authUser.userId(),
                transactionId
        );
        return ApiResponse.success(transaction, transaction.toVoiceMessage());
    }

    /**
     * 목록은 건수만 알린다. 스무 건을 끝까지 읽어 주면 듣는 사람이 따라올 수 없어,
     * 개별 거래는 상세 조회로 넘긴다.
     */
    private String toListVoiceMessage(final PageResponse<TransactionResponse> transactions) {
        if (transactions.totalElements() == 0L) {
            return EMPTY_LIST_VOICE_MESSAGE;
        }
        return "거래가 %d건 있어요.".formatted(transactions.totalElements());
    }
}
