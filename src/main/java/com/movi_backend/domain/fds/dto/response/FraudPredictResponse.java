package com.movi_backend.domain.fds.dto.response;

import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.math.BigDecimal;
import java.util.List;

/**
 * FDS 예측 응답. docs/ai-api-contract.md 3.4의 스키마를 따른다.
 *
 * <p>{@code reasonCodes}에 모르는 코드가 섞여 와도 백엔드는 무시하고 동작해야 한다. AI 파트가
 * 코드를 추가할 때마다 이체가 멈추면 안 된다.
 */
public record FraudPredictResponse(
        String requestId,
        String modelVersion,
        String policyVersion,
        Scores scores,
        RiskLevel riskLevel,
        FdsDecision decision,
        List<String> reasonCodes,
        Integer latencyMs
) {

    public record Scores(
            BigDecimal anomalyScore,
            BigDecimal ruleScore,
            BigDecimal finalRiskScore
    ) {
        public static Scores of(
                final BigDecimal anomalyScore,
                final BigDecimal ruleScore,
                final BigDecimal finalRiskScore
        ) {
            return new Scores(anomalyScore, ruleScore, finalRiskScore);
        }
    }

    public BigDecimal anomalyScore() {
        return scores.anomalyScore();
    }
}
