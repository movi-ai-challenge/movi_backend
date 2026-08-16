package com.movi_backend.domain.voice.client.dto;

import java.math.BigDecimal;

public record VoiceEntityConfidences(
        BigDecimal amount,
        BigDecimal recipient,
        BigDecimal sourceAccountAlias,
        BigDecimal bankName,
        BigDecimal startDate,
        BigDecimal endDate
) {

    public static VoiceEntityConfidences transfer(
            final BigDecimal amount,
            final BigDecimal recipient,
            final BigDecimal sourceAccountAlias
    ) {
        return new VoiceEntityConfidences(
                amount,
                recipient,
                sourceAccountAlias,
                null,
                null,
                null
        );
    }
}
