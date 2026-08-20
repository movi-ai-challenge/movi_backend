package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;

public record FdsScores(
        BigDecimal anomalyScore,
        BigDecimal ruleScore,
        BigDecimal finalRiskScore
) {

    public static FdsScores of(
            final BigDecimal anomalyScore,
            final BigDecimal ruleScore,
            final BigDecimal finalRiskScore
    ) {
        return new FdsScores(anomalyScore, ruleScore, finalRiskScore);
    }
}
