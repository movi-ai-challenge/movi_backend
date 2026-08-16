package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsContextFeature;
import com.movi_backend.domain.fds.client.dto.FdsProfileFeature;
import com.movi_backend.domain.fds.client.dto.FdsRecipientFeature;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockFdsAssessmentClientTest {

    private final MockFdsAssessmentClient client = new MockFdsAssessmentClient();

    @Test
    @DisplayName("신뢰 조건을 충족하는 소액 이체를 평가하면 LOW와 ALLOW를 반환한다")
    void 신뢰_조건을_충족하는_소액_이체를_평가하면_LOW와_ALLOW를_반환한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FdsDecision.ALLOW);
    }

    @Test
    @DisplayName("신규 수취인 이체를 평가하면 MEDIUM과 알림 허용을 반환한다")
    void 신규_수취인_이체를_평가하면_MEDIUM과_알림_허용을_반환한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.requestOf(
                new BigDecimal("50000"),
                FdsRecipientFeature.of(0, true),
                FdsProfileFeature.coldStartProfile(),
                FdsContextFeature.of(true, new BigDecimal("0.93"))
        );

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(response.decision()).isEqualTo(FdsDecision.ALLOW_WITH_ALERT);
    }

    @Test
    @DisplayName("고액 이체를 평가하면 HIGH와 BLOCK을 반환한다")
    void 고액_이체를_평가하면_HIGH와_BLOCK을_반환한다() {
        // given
        final FdsAssessmentRequest request = FdsClientFixture.requestOf(
                new BigDecimal("700000"),
                FdsRecipientFeature.of(5, false),
                FdsProfileFeature.coldStartProfile(),
                FdsContextFeature.of(true, new BigDecimal("0.93"))
        );

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.decision()).isEqualTo(FdsDecision.BLOCK);
    }
}
