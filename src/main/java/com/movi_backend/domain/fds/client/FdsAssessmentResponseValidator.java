package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FdsAssessmentResponseValidator {

    private static final BigDecimal MINIMUM_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAXIMUM_SCORE = BigDecimal.ONE;

    public FdsAssessmentResponse validate(
            final FdsAssessmentRequest request,
            final FdsAssessmentResponse response
    ) {
        validateRequiredFields(request, response);
        validateScores(response.scores());
        validateDecision(response);
        validateLatency(response.latencyMs());
        return response;
    }

    private void validateRequiredFields(
            final FdsAssessmentRequest request,
            final FdsAssessmentResponse response
    ) {
        if (response == null) {
            throw invalidResponse("응답 없음");
        }
        if (!Objects.equals(request.requestId(), response.requestId())) {
            throw invalidResponse("requestId 불일치");
        }
        if (response.modelVersion() == null || response.modelVersion().isBlank()) {
            throw invalidResponse("modelVersion 누락");
        }
        if (response.policyVersion() == null || response.policyVersion().isBlank()) {
            throw invalidResponse("policyVersion 누락");
        }
        if (response.scores() == null) {
            throw invalidResponse("scores 누락");
        }
        if (response.riskLevel() == null || response.decision() == null) {
            throw invalidResponse("risk 또는 decision 누락");
        }
        if (response.reasonCodes() == null) {
            throw invalidResponse("reasonCodes 누락");
        }
    }

    private void validateScores(final FdsScores scores) {
        validateScore(scores.anomalyScore());
        validateScore(scores.ruleScore());
        validateScore(scores.finalRiskScore());
    }

    private void validateScore(final BigDecimal score) {
        if (score == null) {
            throw invalidResponse("score 누락");
        }
        if (score.compareTo(MINIMUM_SCORE) < 0 || score.compareTo(MAXIMUM_SCORE) > 0) {
            throw invalidResponse("score 범위 오류");
        }
    }

    private void validateDecision(final FdsAssessmentResponse response) {
        final FdsDecision expectedDecision = FdsDecision.from(response.riskLevel());
        if (response.decision() != expectedDecision) {
            throw invalidResponse("risk와 decision 조합 오류");
        }
    }

    private void validateLatency(final Integer latencyMs) {
        if (latencyMs == null || latencyMs < 0) {
            throw invalidResponse("latencyMs 범위 오류");
        }
    }

    private BusinessException invalidResponse(final String detailMessage) {
        return new BusinessException(ErrorCode.ASSESSMENT_FAILED, detailMessage);
    }
}
