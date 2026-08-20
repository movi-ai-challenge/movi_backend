package com.movi_backend.domain.transfer.dto.request;

import java.math.BigDecimal;

public record TransferCommandRequest(
        Long amount,
        String recipient,
        String sourceAccountAlias,
        BigDecimal sttConfidence,
        BigDecimal intentConfidence,
        BigDecimal amountConfidence,
        BigDecimal recipientConfidence
) {

    public static TransferCommandRequest of(
            final Long amount,
            final String recipient,
            final String sourceAccountAlias,
            final BigDecimal sttConfidence,
            final BigDecimal intentConfidence,
            final BigDecimal amountConfidence,
            final BigDecimal recipientConfidence
    ) {
        return new TransferCommandRequest(
                amount,
                recipient,
                sourceAccountAlias,
                sttConfidence,
                intentConfidence,
                amountConfidence,
                recipientConfidence
        );
    }
}
