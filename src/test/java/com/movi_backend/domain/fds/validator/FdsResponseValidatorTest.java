package com.movi_backend.domain.fds.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FdsResponseValidatorTest {

    private static final String REQUEST_ID = "fds-transfer-101";

    private final FdsResponseValidator validator = new FdsResponseValidator();

    @Test
    @DisplayName("허용된 위험도-결정 조합은 통과시킨다")
    void 허용된_조합은_통과한다() {
        // given
        final FraudPredictRequest request = request();
        final FraudPredictResponse response =
                response(REQUEST_ID, RiskLevel.HIGH, FdsDecision.BLOCK, new BigDecimal("0.82"));

        // when & then
        assertThatCode(() -> validator.validate(request, response)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("위험도와 결정이 어긋나면 평가 실패로 처리한다")
    void 위험도와_결정이_어긋나면_거부한다() {
        // given
        final FraudPredictRequest request = request();
        final FraudPredictResponse response =
                response(REQUEST_ID, RiskLevel.HIGH, FdsDecision.ALLOW, new BigDecimal("0.82"));

        // when & then
        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
    }

    @Test
    @DisplayName("다른 요청의 평가 결과는 받아들이지 않는다")
    void 요청_식별자가_다르면_거부한다() {
        // given
        final FraudPredictRequest request = request();
        final FraudPredictResponse response =
                response("fds-transfer-999", RiskLevel.LOW, FdsDecision.ALLOW, new BigDecimal("0.1"));

        // when & then
        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
    }

    @Test
    @DisplayName("점수가 0~1 범위를 벗어나면 거부한다")
    void 점수_범위를_벗어나면_거부한다() {
        // given
        final FraudPredictRequest request = request();
        final FraudPredictResponse response =
                response(REQUEST_ID, RiskLevel.LOW, FdsDecision.ALLOW, new BigDecimal("1.5"));

        // when & then
        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(BusinessException.class);
    }

    private FraudPredictRequest request() {
        return new FraudPredictRequest(
                REQUEST_ID,
                101L,
                3L,
                50_000L,
                320_000L,
                LocalDateTime.now(),
                FraudPredictRequest.RecipientFeature.unknown(),
                FraudPredictRequest.ProfileFeature.emptyHistory(),
                FraudPredictRequest.ContextFeature.of(true, 0.93d)
        );
    }

    private FraudPredictResponse response(
            final String requestId,
            final RiskLevel riskLevel,
            final FdsDecision decision,
            final BigDecimal score
    ) {
        return new FraudPredictResponse(
                requestId,
                "isolation-forest-v1",
                "risk-policy-v1",
                FraudPredictResponse.Scores.of(score, score, score),
                riskLevel,
                decision,
                List.of("HIGH_AMOUNT"),
                57
        );
    }
}
