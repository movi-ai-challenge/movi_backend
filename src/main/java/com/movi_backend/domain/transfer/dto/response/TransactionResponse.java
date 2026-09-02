package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.fds.type.RiskLevel;
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
        TransactionSource source,
        /** FDS 판정. 우리 서비스를 거치지 않은 거래는 null 이다. */
        RiskLevel riskLevel
) {

    public static TransactionResponse from(final Transaction transaction) {
        return from(transaction, null);
    }

    /**
     * FDS 판정을 함께 실어 준다.
     *
     * <p>거래내역에서 "이 거래가 위험하다고 잡혔다"를 보여 주기 위한 값이다. 우리
     * 서비스를 거치지 않은 거래(은행에서 내려받은 입출금)는 평가가 없어 {@code null}이다.
     */
    public static TransactionResponse from(
            final Transaction transaction,
            final RiskLevel riskLevel
    ) {
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
                transaction.getSource(),
                riskLevel
        );
    }
}
