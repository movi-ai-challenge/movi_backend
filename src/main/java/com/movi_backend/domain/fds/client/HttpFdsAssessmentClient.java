package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.client.dto.FraudDetectionRequest;
import com.movi_backend.domain.fds.client.dto.FraudDetectionResponse;
import com.movi_backend.domain.fds.client.dto.TransactionData;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 실제 AI FDS 서버와 통신한다.
 *
 * <p>여기서만 우리 내부 계약({@link FdsAssessmentRequest}/{@link FdsAssessmentResponse})과
 * AI 서버 계약({@link FraudDetectionRequest}/{@link FraudDetectionResponse})을 서로 옮긴다.
 * 나머지 코드(검증기·저장·{@code TransferExecutionService})는 AI 서버가 실제로 어떤 필드를
 * 쓰는지 몰라도 된다.
 */
@Component
@ConditionalOnProperty(prefix = "movi.fds", name = "client-type", havingValue = "http")
public class HttpFdsAssessmentClient implements FdsAssessmentClient {

    private static final String ASSESSMENT_PATH = "/api/v1/fraud/detect";
    private static final String DEFAULT_TRANSACTION_TYPE = "TRANSFER";
    private static final String MEDIUM_VOICE = "VOICE";
    private static final String MEDIUM_APP = "APP";

    /**
     * AI 서버는 model만 응답에 싣고 규칙 엔진·정책 버전은 아직 따로 내려주지 않는다.
     * moviback.duckdns.org/ai/fds/openapi.json 의 info.version 이다.
     * AI 팀이 응답에 자체 버전 필드를 추가하면 이 상수는 지운다.
     */
    private static final String FALLBACK_POLICY_VERSION = "movi-fraud-detection-api-0.4.0";

    private static final BigDecimal SCORE_SCALE_DIVISOR = new BigDecimal("100");
    private static final int SCORE_DIVISION_SCALE = 6;

    private final RestClient restClient;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    public HttpFdsAssessmentClient(
            @Qualifier("fdsRestClient") final RestClient restClient,
            final SensitiveDataCrypto sensitiveDataCrypto
    ) {
        this.restClient = restClient;
        this.sensitiveDataCrypto = sensitiveDataCrypto;
    }

    @Override
    public FdsAssessmentResponse assess(final FdsAssessmentRequest request) {
        final long startedAt = System.currentTimeMillis();
        try {
            final FraudDetectionResponse response = restClient.post()
                    .uri(ASSESSMENT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toFraudDetectionRequest(request))
                    .retrieve()
                    .body(FraudDetectionResponse.class);
            final int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            return toAssessmentResponse(request, response, latencyMs);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final ResourceAccessException exception) {
            if (containsTimeout(exception)) {
                throw assessmentTimeout(exception);
            }
            throw assessmentFailed(exception);
        } catch (final RestClientException exception) {
            if (isGatewayTimeout(exception)) {
                throw assessmentTimeout(exception);
            }
            throw assessmentFailed(exception);
        }
    }

    private FraudDetectionRequest toFraudDetectionRequest(final FdsAssessmentRequest request) {
        return FraudDetectionRequest.of(TransactionData.of(
                request.fromFintechUseNum(),
                sensitiveDataCrypto.decrypt(request.toAccountNumEncrypted()),
                request.fromBankCode(),
                request.toBankCode(),
                DEFAULT_TRANSACTION_TYPE,
                request.amount(),
                request.requestedAt(),
                mediumOf(request)
        ));
    }

    /** 발화 신뢰도가 있으면 음성 경로, 없으면 화면 직접 입력 경로다. */
    private String mediumOf(final FdsAssessmentRequest request) {
        if (request.context().sttConfidence() != null) {
            return MEDIUM_VOICE;
        }
        return MEDIUM_APP;
    }

    private FdsAssessmentResponse toAssessmentResponse(
            final FdsAssessmentRequest request,
            final FraudDetectionResponse response,
            final int latencyMs
    ) {
        if (response == null) {
            throw assessmentFailed("응답 없음");
        }
        final RiskLevel riskLevel = parseRiskLevel(response.riskLevel());
        return FdsAssessmentResponse.of(
                request.requestId(),
                blankToNull(response.model(), "isolation_forest"),
                FALLBACK_POLICY_VERSION,
                toScores(response),
                riskLevel,
                FdsDecision.from(riskLevel),
                triggeredRulesOf(response),
                latencyMs
        );
    }

    private FdsScores toScores(final FraudDetectionResponse response) {
        return FdsScores.of(
                requireScore(response.anomalyScore(), "anomaly_score"),
                toUnitScale(requireScore(response.ruleScore(), "rule_score")),
                toUnitScale(requireScore(response.finalRiskScore(), "final_risk_score"))
        );
    }

    private BigDecimal requireScore(final BigDecimal score, final String fieldName) {
        if (score == null) {
            throw assessmentFailed(fieldName + " 누락");
        }
        return score;
    }

    /**
     * AI 응답의 rule_score·final_risk_score는 0~100이다. 내부 계약은 세 점수 모두 0~1을
     * 가정하므로 단위만 맞춘다 - 값의 의미를 다시 정의하는 게 아니다.
     */
    private BigDecimal toUnitScale(final BigDecimal hundredScaleScore) {
        return hundredScaleScore.divide(
                SCORE_SCALE_DIVISOR,
                SCORE_DIVISION_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private RiskLevel parseRiskLevel(final String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            throw assessmentFailed("risk_level 누락");
        }
        try {
            return RiskLevel.valueOf(riskLevel.strip().toUpperCase());
        } catch (final IllegalArgumentException exception) {
            throw assessmentFailed("알 수 없는 risk_level: " + riskLevel);
        }
    }

    private List<String> triggeredRulesOf(final FraudDetectionResponse response) {
        if (response.triggeredRules() == null) {
            return List.of();
        }
        return List.copyOf(response.triggeredRules());
    }

    private String blankToNull(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private boolean isGatewayTimeout(final RestClientException exception) {
        return exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT;
    }

    private boolean containsTimeout(final Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private BusinessException assessmentFailed(final String detailMessage) {
        return new BusinessException(ErrorCode.ASSESSMENT_FAILED, detailMessage);
    }

    private BusinessException assessmentFailed(final Exception exception) {
        return new BusinessException(
                ErrorCode.ASSESSMENT_FAILED,
                exception.getClass().getSimpleName()
        );
    }

    private BusinessException assessmentTimeout(final Exception exception) {
        return new BusinessException(
                ErrorCode.ASSESSMENT_TIMEOUT,
                exception.getClass().getSimpleName()
        );
    }
}
