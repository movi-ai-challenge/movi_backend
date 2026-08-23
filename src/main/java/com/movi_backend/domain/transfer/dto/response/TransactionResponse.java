package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import java.time.LocalDateTime;

/** 거래내역 목록 항목. 계좌번호는 민감정보이므로 응답에 포함하지 않는다. */
public record TransactionResponse(
        Long transactionId,
        Long accountId,
        TransactionType type,
        Long amount,
        Long balanceAfter,
        String counterpartyName,
        String category,
        LocalDateTime transactedAt,
        String memo,
        TransactionSource source
) {

    public static TransactionResponse from(final Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getTranType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyName(),
                transaction.getCategory(),
                transaction.getTranDatetime(),
                transaction.getMemo(),
                transaction.getSource()
        );
    }
}
