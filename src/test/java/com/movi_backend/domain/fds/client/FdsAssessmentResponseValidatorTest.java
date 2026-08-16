package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FdsAssessmentResponseValidatorTest {

    private final FdsAssessmentResponseValidator validator =
            new FdsAssessmentResponseValidator();

    @Test
    @DisplayName("계약에 맞는 FDS 응답을 검증하면 원본 응답을 반환한다")
    void 계약에_맞는_FDS_응답을_검증하면_원본_응답을_반환한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = validResponse(
                request.requestId(),
                RiskLevel.LOW,
                FdsDecision.ALLOW
        );

        // when
        final FdsAssessmentResponse validated = validator.validate(request, response);

        // then
        assertThat(validated).isSameAs(response);
    }

    @Test
    @DisplayName("응답의 요청 ID가 다르면 위험도 평가 예외가 발생한다")
    void 응답의_요청_ID가_다르면_위험도_평가_예외가_발생한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = validResponse(
                "other-request",
                RiskLevel.LOW,
                FdsDecision.ALLOW
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertAssessmentFailed(thrown);
    }

    @Test
    @DisplayName("응답 점수가 범위를 벗어나면 위험도 평가 예외가 발생한다")
    void 응답_점수가_범위를_벗어나면_위험도_평가_예외가_발생한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = FdsClientFixture.responseOf(
                request.requestId(),
                FdsScores.of(
                        new BigDecimal("1.01"),
                        new BigDecimal("0.2"),
                        new BigDecimal("0.3")
                ),
                RiskLevel.LOW,
                FdsDecision.ALLOW,
                57
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertAssessmentFailed(thrown);
    }

    @Test
    @DisplayName("응답 점수가 누락되면 위험도 평가 예외가 발생한다")
    void 응답_점수가_누락되면_위험도_평가_예외가_발생한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = FdsClientFixture.responseOf(
                request.requestId(),
                FdsScores.of(null, new BigDecimal("0.2"), new BigDecimal("0.3")),
                RiskLevel.LOW,
                FdsDecision.ALLOW,
                57
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertAssessmentFailed(thrown);
    }

    @Test
    @DisplayName("위험도와 결정 조합이 다르면 위험도 평가 예외가 발생한다")
    void 위험도와_결정_조합이_다르면_위험도_평가_예외가_발생한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = validResponse(
                request.requestId(),
                RiskLevel.HIGH,
                FdsDecision.ALLOW
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertAssessmentFailed(thrown);
    }

    @Test
    @DisplayName("응답 지연 시간이 누락되면 위험도 평가 예외가 발생한다")
    void 응답_지연_시간이_누락되면_위험도_평가_예외가_발생한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        final FdsAssessmentResponse response = FdsClientFixture.responseOf(
                request.requestId(),
                validScores(),
                RiskLevel.LOW,
                FdsDecision.ALLOW,
                null
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertAssessmentFailed(thrown);
    }

    private FdsAssessmentResponse validResponse(
            final String requestId,
            final RiskLevel riskLevel,
            final FdsDecision decision
    ) {
        return FdsClientFixture.responseOf(
                requestId,
                validScores(),
                riskLevel,
                decision,
                57
        );
    }

    private FdsScores validScores() {
        return FdsScores.of(
                new BigDecimal("0.2"),
                new BigDecimal("0.3"),
                new BigDecimal("0.25")
        );
    }

    private void assertAssessmentFailed(final Throwable thrown) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
    }
}
