package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsHistoryEntry;
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
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
@ConditionalOnProperty(prefix = "movi.fds", name = "client-type", havingValue = "http")
public class HttpFdsAssessmentClient implements FdsAssessmentClient {

    private static final String ASSESSMENT_PATH = "/api/v1/fraud/detect";
    private static final String DEFAULT_TRANSACTION_TYPE = "TRANSFER";
    private static final String MEDIUM_VOICE = "VOICE";
    private static final String MEDIUM_APP = "APP";

    /**
     * AI 로 보내는 거래 식별자 접두어.
     *
     * <p>현재 거래는 {@code transfers}, 이력은 {@code transactions} 에서 온다. 서로 다른
     * 테이블이라 숫자가 우연히 같을 수 있는데, AI 는 한 요청 안에서 식별자가 겹치면 요청
     * 전체를 400 으로 거절한다. 어느 쪽에서 온 값인지 접두어로 갈라 둔다.
     */
    private static final String CURRENT_TRANSACTION_ID_PREFIX = "transfer-";
    private static final String HISTORY_TRANSACTION_ID_PREFIX = "tx-";

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
        final String medium = mediumOf(request);
        return FraudDetectionRequest.of(
                TransactionData.of(
                        CURRENT_TRANSACTION_ID_PREFIX + request.transferId(),
                        request.fromFintechUseNum(),
                        sensitiveDataCrypto.decrypt(request.toAccountNumEncrypted()),
                        request.fromBankCode(),
                        request.toBankCode(),
                        DEFAULT_TRANSACTION_TYPE,
                        request.amount(),
                        request.requestedAt(),
                        medium
                ),
                toHistory(request, medium)
        );
    }

    /**
     * 과거 출금을 AI 스키마로 옮긴다.
     *
     * <p>{@code receiver_bank}는 출금계좌의 은행 코드로 채운다 — {@code transactions}에 상대
     * 은행 코드가 없고, AI 가 이력의 은행 코드를 쓰지 않는 것을 확인했다
     * ({@link FraudDetectionRequest} 참고).
     *
     * <p>{@code medium}은 현재 거래와 같은 값으로 보낸다. 거래별 유입 경로를 저장하지 않아
     * 과거 경로를 알 수 없는데, 여기에 임의의 값을 넣으면 AI 의 {@code UNUSUAL_MEDIUM} 이
     * 사실과 무관하게 발동한다. 특히 음성이 기본 경로인 이 서비스에서 이력을 전부 APP 으로
     * 적으면 <b>정상적인 음성 송금이 매번 경로 이상으로 잡힌다</b> — 없는 정보로 위험 신호를
     * 만들지 않기 위해 규칙이 발동하지 않는 쪽(같은 값)을 택했다.
     */
    private List<TransactionData> toHistory(
            final FdsAssessmentRequest request,
            final String medium
    ) {
        return request.history().stream()
                .map(entry -> toHistoryTransaction(request, entry, medium))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 복호화에 실패한 이력은 버린다.
     *
     * <p>이력은 보조 정보이므로, 한 건이 깨졌다고 평가 자체를 실패시켜 이체를 막지 않는다.
     * 오픈뱅킹에서 내려받아 저장한 거래처럼 우리 키로 암호화되지 않은 값이 섞일 수 있다.
     */
    private TransactionData toHistoryTransaction(
            final FdsAssessmentRequest request,
            final FdsHistoryEntry entry,
            final String medium
    ) {
        final String receiverAccount = decryptOrNull(entry.counterpartyAccountEncrypted());
        if (receiverAccount == null) {
            return null;
        }
        return TransactionData.of(
                HISTORY_TRANSACTION_ID_PREFIX + entry.transactionId(),
                request.fromFintechUseNum(),
                receiverAccount,
                request.fromBankCode(),
                request.fromBankCode(),
                DEFAULT_TRANSACTION_TYPE,
                entry.amount(),
                entry.occurredAt(),
                medium
        );
    }

    private String decryptOrNull(final String encrypted) {
        try {
            return sensitiveDataCrypto.decrypt(encrypted);
        } catch (final RuntimeException exception) {
            log.debug("FDS 이력 계좌번호를 복호화하지 못해 건너뜁니다.", exception);
            return null;
        }
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
