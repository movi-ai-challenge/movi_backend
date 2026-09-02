package com.movi_backend.domain.fds.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * 실제 AI FDS 서버의 응답. AI 서버 계약을 그대로 옮긴 것이다.
 *
 * <p>{@code ruleScore}·{@code finalRiskScore}는 <b>0~100 스케일</b>이다. Isolation Forest가
 * 직접 내는 {@code anomalyScore}만 0~1이다. 우리 내부 {@link FdsScores}는 세 값 모두 0~1로
 * 가정하므로, 이 record에서 {@link FdsScores}로 옮길 때 두 값을 100으로 나눠 맞춘다 — 값의
 * 의미를 재해석하는 게 아니라 단위만 맞추는 것이며, 그 변환은 어댑터에만 있고 이 record
 * 자체는 AI가 준 값을 그대로 담는다.
 *
 * <p>{@code riskLevel}은 openapi 문서에는 nullable로 돼 있지만 실제로는 항상 채워져 온다
 * (2026-09-02 실제 호출로 확인, LOW/MEDIUM/HIGH). 그래도 비어 있거나 모르는 값이 오면
 * 어댑터가 평가 실패로 처리하고 이체를 진행하지 않는다.
 */
public record FraudDetectionResponse(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("anomaly_score") BigDecimal anomalyScore,
        BigDecimal threshold,
        @JsonProperty("is_anomaly") Boolean isAnomaly,
        String model,
        @JsonProperty("rule_score") BigDecimal ruleScore,
        @JsonProperty("final_risk_score") BigDecimal finalRiskScore,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("triggered_rules") List<String> triggeredRules
) {
}
