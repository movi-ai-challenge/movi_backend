package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.CryptoProperties;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.net.SocketTimeoutException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * https://moviback.duckdns.org/ai/fds/openapi.json 을 그대로 검증한다. 필드명이 snake_case인
 * 것도, rule_score·final_risk_score가 0~100인 것도 실제 서버에 요청을 보내 확인한 값이다.
 */
class HttpFdsAssessmentClientTest {

    private static final CryptoProperties CRYPTO_PROPERTIES = new CryptoProperties(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    );
    private static final SensitiveDataCrypto CRYPTO = new SensitiveDataCrypto(CRYPTO_PROPERTIES);

    private static final String RESPONSE_JSON = """
            {
              "transaction_id": null,
              "anomaly_score": 0.344090,
              "threshold": 0.446117,
              "is_anomaly": false,
              "model": "isolation_forest",
              "rule_score": 20.0,
              "final_risk_score": 12.0,
              "risk_level": "LOW",
              "triggered_rules": ["NEW_RECIPIENT", "CROSS_BANK"]
            }
            """;

    @Test
    @DisplayName("실제 AI 스키마로 요청을 보내고 0~100 점수를 0~1로 맞춰 반환한다")
    void 실제_AI_스키마로_요청을_보내고_점수_스케일을_맞춘다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 실제 AI 계약의 필드명이다. camelCase가 아니라 snake_case로 나가야 한다.
                .andExpect(content().string(Matchers.containsString("\"current_transaction\"")))
                .andExpect(content().string(Matchers.containsString("\"sender_account\"")))
                .andExpect(content().string(Matchers.containsString("\"receiver_account\":\"110123456789\"")))
                .andExpect(content().string(Matchers.containsString("\"history\":[]")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.requestId()).isEqualTo(request.requestId());
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.decision()).isEqualTo(FdsDecision.ALLOW);
        assertThat(response.reasonCodes()).containsExactly("NEW_RECIPIENT", "CROSS_BANK");
        // rule_score 20.0, final_risk_score 12.0 은 0~100 스케일이다. 0~1로 나눠 맞춘다.
        assertThat(response.scores().ruleScore()).isEqualByComparingTo("0.2");
        assertThat(response.scores().finalRiskScore()).isEqualByComparingTo("0.12");
        assertThat(response.scores().anomalyScore()).isEqualByComparingTo("0.344090");
        server.verify();
    }

    @Test
    @DisplayName("수취인 계좌를 복호화해서 평문으로 보낸다")
    void 수취인_계좌를_복호화해서_평문으로_보낸다() {
        // given
        final String plainAccountNumber = "004987654321";
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient(plainAccountNumber);

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andExpect(content().string(
                        Matchers.containsString("\"receiver_account\":\"" + plainAccountNumber + "\"")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        client.assess(request);

        // then
        server.verify();
    }

    @Test
    @DisplayName("과거 출금을 history 에 실어 보낸다 - 이력이 비면 AI 가 금액 이상을 잡지 못한다")
    void 과거_출금을_history_에_실어_보낸다() {
        // given
        final String plainCounterparty = "110999888777";
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = FdsClientFixture.requestWithHistory(
                CRYPTO.encrypt("110123456789"),
                FdsClientFixture.historyOf(2, CRYPTO.encrypt(plainCounterparty))
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andExpect(method(HttpMethod.POST))
                // 이력도 현재 거래와 같은 snake_case 스키마로 나간다.
                .andExpect(content().string(Matchers.containsString("\"history\":[{")))
                .andExpect(content().string(Matchers.containsString("\"amount\":10000")))
                .andExpect(content().string(Matchers.containsString("\"amount\":20000")))
                // 이력의 계좌도 복호화해 보낸다. 현재 수취인과 표기가 어긋나면 재이체인데도
                // AI 가 매번 NEW_RECIPIENT 로 잡는다.
                .andExpect(content().string(
                        Matchers.containsString("\"receiver_account\":\"" + plainCounterparty + "\"")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        client.assess(request);

        // then
        server.verify();
    }

    @Test
    @DisplayName("AI 가 준 정책 버전을 그대로 기록한다")
    void AI_가_준_정책_버전을_기록한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andRespond(withSuccess("""
                        {
                          "anomaly_score": 0.344090,
                          "threshold": 0.446117,
                          "is_anomaly": false,
                          "model": "isolation_forest",
                          "rule_score": 20.0,
                          "final_risk_score": 12.0,
                          "risk_level": "LOW",
                          "triggered_rules": [],
                          "policy_version": "0.6.0"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then
        assertThat(response.policyVersion()).isEqualTo("0.6.0");
        server.verify();
    }

    @Test
    @DisplayName("정책 버전을 안 주는 옛 AI 서버면 대체값을 남긴다 - 기록만 보고 실제 버전이라 오해하지 않게 한다")
    void 정책_버전이_없으면_대체값을_남긴다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");

        // RESPONSE_JSON 에는 policy_version 이 없다.
        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then - 특정 버전을 사실처럼 적지 않는다.
        assertThat(response.policyVersion()).isEqualTo("movi-fraud-detection-api-unknown");
        server.verify();
    }

    @Test
    @DisplayName("현재 거래와 이력에 서로 다른 transaction_id 를 붙인다 - 겹치면 AI 가 요청 전체를 거절한다")
    void 거래_식별자가_겹치지_않게_보낸다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = FdsClientFixture.requestWithHistory(
                CRYPTO.encrypt("110123456789"),
                FdsClientFixture.historyOf(2, CRYPTO.encrypt("110999888777"))
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                // 현재 거래는 transfers, 이력은 transactions 에서 온다. 숫자만 보내면 서로 다른
                // 테이블의 같은 값이 겹칠 수 있어 접두어로 가른다.
                .andExpect(content().string(
                        Matchers.containsString("\"transaction_id\":\"transfer-101\"")))
                .andExpect(content().string(
                        Matchers.containsString("\"transaction_id\":\"tx-1\"")))
                .andExpect(content().string(
                        Matchers.containsString("\"transaction_id\":\"tx-2\"")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        client.assess(request);

        // then
        server.verify();
    }

    @Test
    @DisplayName("이력의 medium 은 현재 거래와 같게 보낸다 - 없는 정보로 UNUSUAL_MEDIUM 을 만들지 않는다")
    void 이력의_medium_은_현재_거래와_같게_보낸다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        // sttConfidence 가 있으므로 현재 거래의 medium 은 VOICE 다.
        final FdsAssessmentRequest request = FdsClientFixture.requestWithHistory(
                CRYPTO.encrypt("110123456789"),
                FdsClientFixture.historyOf(1, CRYPTO.encrypt("110999888777"))
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"medium\":\"APP\""))))
                .andExpect(content().string(Matchers.containsString("\"medium\":\"VOICE\"")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        client.assess(request);

        // then
        server.verify();
    }

    @Test
    @DisplayName("복호화되지 않는 이력은 건너뛰고 평가를 계속한다")
    void 복호화되지_않는_이력은_건너뛴다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        // 오픈뱅킹에서 받아 저장한 거래처럼 우리 키로 암호화되지 않은 값이 섞일 수 있다.
        final FdsAssessmentRequest request = FdsClientFixture.requestWithHistory(
                CRYPTO.encrypt("110123456789"),
                FdsClientFixture.historyOf(1, "not-encrypted-at-all")
        );

        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andExpect(content().string(Matchers.containsString("\"history\":[]")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        final FdsAssessmentResponse response = client.assess(request);

        // then - 이력 한 건이 깨졌다고 이체가 막히면 안 된다.
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        server.verify();
    }

    @Test
    @DisplayName("risk_level 이 비어 있으면 위험도 평가 실패로 처리하고 이체를 진행하지 않는다")
    void risk_level_이_비어_있으면_평가_실패로_처리한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");
        final String responseWithoutRiskLevel = """
                {
                  "anomaly_score": 0.3,
                  "threshold": 0.44,
                  "is_anomaly": false,
                  "model": "isolation_forest",
                  "rule_score": 10.0,
                  "final_risk_score": 8.0,
                  "risk_level": null,
                  "triggered_rules": []
                }
                """;
        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andRespond(withSuccess(responseWithoutRiskLevel, MediaType.APPLICATION_JSON));

        // when
        final Throwable thrown = catchThrowable(() -> client.assess(request));

        // then — AI가 규칙엔진 판정을 못 내려도 조용히 통과시키지 않는다.
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
    }

    @Test
    @DisplayName("FDS API가 실패하면 위험도 평가 예외가 발생한다")
    void FDS_API가_실패하면_위험도_평가_예외가_발생한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");
        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
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

    @Test
    @DisplayName("FDS API가 HTTP 504를 반환하면 위험도 평가 시간 초과 예외가 발생한다")
    void FDS_API가_HTTP_504를_반환하면_위험도_평가_시간_초과_예외가_발생한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");
        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        // when
        final Throwable thrown = catchThrowable(() -> client.assess(request));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TIMEOUT);
        server.verify();
    }

    @Test
    @DisplayName("FDS API 응답이 시간 초과되면 위험도 평가 시간 초과 예외가 발생한다")
    void FDS_API_응답이_시간_초과되면_위험도_평가_시간_초과_예외가_발생한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpFdsAssessmentClient client = new HttpFdsAssessmentClient(builder.build(), CRYPTO);
        final FdsAssessmentRequest request = requestWithEncryptedRecipient("110123456789");
        server.expect(once(), requestTo("http://localhost:8000/api/v1/fraud/detect"))
                .andRespond(ignored -> {
                    throw new ResourceAccessException(
                            "FDS 응답 시간 초과",
                            new SocketTimeoutException("read timed out")
                    );
                });

        // when
        final Throwable thrown = catchThrowable(() -> client.assess(request));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TIMEOUT);
        server.verify();
    }

    private FdsAssessmentRequest requestWithEncryptedRecipient(final String plainAccountNumber) {
        return FdsClientFixture.normalRequestWithRecipientAccount(CRYPTO.encrypt(plainAccountNumber));
    }
}
