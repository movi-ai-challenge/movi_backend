package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpFdsAssessmentClientTest {

    private static final String RESPONSE_JSON = """
            {
              "requestId": "fds-transfer-101",
              "modelVersion": "isolation-forest-v1",
              "policyVersion": "risk-policy-v1",
              "scores": {
                "anomalyScore": 0.22,
                "ruleScore": 0.15,
                "finalRiskScore": 0.18
              },
              "riskLevel": "LOW",
              "decision": "ALLOW",
              "reasonCodes": [],
              "latencyMs": 57
            }
            """;

    @Test
    @DisplayName("FDS API를 호출하면 JSON 계약을 전송하고 검증된 응답을 반환한다")
    void FDS_API를_호출하면_JSON_계약을_전송하고_검증된_응답을_반환한다() {
        // given
        final RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(
                builder.build(),
                new FdsAssessmentResponseValidator()
        );
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        server.expect(once(), requestTo("http://localhost:8000/internal/v1/fraud/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.containsString("fds-transfer-101")))
                .andExpect(content().string(Matchers.containsString("balanceBefore")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FdsDecision.ALLOW);
        server.verify();
    }

    @Test
    @DisplayName("FDS API가 실패하면 위험도 평가 예외가 발생한다")
    void FDS_API가_실패하면_위험도_평가_예외가_발생한다() {
        // given
        final RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(
                builder.build(),
                new FdsAssessmentResponseValidator()
        );
        final FdsAssessmentRequest request = FdsClientFixture.normalRequest();
        server.expect(once(), requestTo("http://localhost:8000/internal/v1/fraud/predict"))
                .andRespond(withServerError());

        // when
        final Throwable thrown = catchThrowable(() -> client.assess(request));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
        server.verify();
    }
}
