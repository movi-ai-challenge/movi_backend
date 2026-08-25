package com.movi_backend.domain.fds.validator;

import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * FDS 응답 검증. docs/ai-api-contract.md 3.5.
 *
 * <p><b>AI 응답을 그대로 믿지 않는다.</b> 이 값 하나로 남의 돈이 나가거나 막힌다.
 * {@code HIGH + ALLOW}처럼 위험도와 결정이 어긋난 응답은 모델 배포 사고이거나 응답이 섞인
 * 것이므로, 통과시키지 않고 평가 실패로 처리한다.
 */
@Component
public class FdsResponseValidator {

    private static final BigDecimal MINIMUM_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAXIMUM_SCORE = BigDecimal.ONE;

    public void validate(final FraudPredictRequest request, final FraudPredictResponse response) {
        validateRequestId(request, response);
        validateVersions(response);
        validateScores(response);
        validateDecisionCombination(response);
        validateLatency(response);
    }

    /** 다른 이체의 평가 결과가 섞여 들어오는 것을 막는다. */
    private void validateRequestId(
            final FraudPredictRequest request,
            final FraudPredictResponse response
    ) {
        if (request.requestId().equals(response.requestId())) {
            return;
        }
        throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "요청 식별자 불일치");
    }

    /** 어떤 모델·정책으로 판정했는지 남기지 못하면 사후 검증이 불가능하다. */
    private void validateVersions(final FraudPredictResponse response) {
        if (isBlank(response.modelVersion()) || isBlank(response.policyVersion())) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "버전 정보 누락");
        }
    }

    private void validateScores(final FraudPredictResponse response) {
        if (response.scores() == null) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "점수 누락");
        }
        validateScoreRange(response.scores().anomalyScore());
        validateScoreRange(response.scores().ruleScore());
        validateScoreRange(response.scores().finalRiskScore());
    }

    private void validateScoreRange(final BigDecimal score) {
        if (score == null) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "점수 누락");
        }
        if (score.compareTo(MINIMUM_SCORE) < 0 || score.compareTo(MAXIMUM_SCORE) > 0) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "점수 범위 초과");
        }
    }

    /**
     * 허용 조합은 셋뿐이다.
     *
     * <pre>
     * LOW    + ALLOW
     * MEDIUM + ALLOW_WITH_ALERT
     * HIGH   + BLOCK
     * </pre>
     */
    private void validateDecisionCombination(final FraudPredictResponse response) {
        final RiskLevel riskLevel = response.riskLevel();
        final FdsDecision decision = response.decision();
        if (riskLevel == null || decision == null) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "위험도 또는 결정 누락");
        }
        if (FdsDecision.from(riskLevel) == decision) {
            return;
        }
        throw new BusinessException(
                ErrorCode.ASSESSMENT_FAILED,
                "위험도-결정 불일치 %s/%s".formatted(riskLevel, decision)
        );
    }

    private void validateLatency(final FraudPredictResponse response) {
        if (response.latencyMs() == null || response.latencyMs() < 0) {
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "지연 시간 값 오류");
        }
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
