package com.movi_backend.domain.fds.infrastructure;

import com.movi_backend.domain.fds.config.FdsProperties;
import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 추론 API가 열리기 전까지 쓰는 대역.
 *
 * <p>금액과 수취인 이력만으로 판정한다. <b>실제 이상거래 탐지가 아니다.</b> 백엔드의 분기 흐름
 * (차단·알림·완료)을 끝까지 검증하기 위한 것이며, AI 파트 API가 준비되면
 * {@code movi.fds.mode=http}로 바꾸기만 하면 된다.
 *
 * <p>판정 규칙은 실제 모델과 무관하게 결정적이어서 시연·테스트에서 같은 입력에 같은 결과가 나온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movi.fds", name = "mode", havingValue = FdsProperties.MODE_MOCK, matchIfMissing = true)
public class MockFdsClient implements FdsClient {

    private static final String MOCK_MODEL_VERSION = "mock-isolation-forest-v0";
    private static final String MOCK_POLICY_VERSION = "mock-risk-policy-v0";
    private static final int MOCK_LATENCY_MS = 5;
    private static final BigDecimal HIGH_SCORE = new BigDecimal("0.82");
    private static final BigDecimal MEDIUM_SCORE = new BigDecimal("0.55");
    private static final BigDecimal LOW_SCORE = new BigDecimal("0.12");
    private static final String REASON_HIGH_AMOUNT = "HIGH_AMOUNT";
    private static final String REASON_NEW_RECIPIENT = "NEW_RECIPIENT";
    private static final String REASON_COLD_START = "COLD_START";

    private final FdsProperties fdsProperties;

    @Override
    public FraudPredictResponse predict(final FraudPredictRequest request) {
        final RiskLevel riskLevel = decideRiskLevel(request);
        final BigDecimal score = scoreOf(riskLevel);
        log.info("[MOCK FDS] transferId={} riskLevel={}", request.transferId(), riskLevel);

        return new FraudPredictResponse(
                request.requestId(),
                MOCK_MODEL_VERSION,
                MOCK_POLICY_VERSION,
                FraudPredictResponse.Scores.of(score, score, score),
                riskLevel,
                FdsDecision.from(riskLevel),
                reasonCodesOf(request, riskLevel),
                MOCK_LATENCY_MS
        );
    }

    private RiskLevel decideRiskLevel(final FraudPredictRequest request) {
        if (request.amount() >= fdsProperties.mockHighAmount()) {
            return RiskLevel.HIGH;
        }
        if (request.amount() >= fdsProperties.mockMediumAmount()) {
            return RiskLevel.MEDIUM;
        }
        if (request.recipient().firstTime() && request.profile().coldStart()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private BigDecimal scoreOf(final RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.HIGH) {
            return HIGH_SCORE;
        }
        if (riskLevel == RiskLevel.MEDIUM) {
            return MEDIUM_SCORE;
        }
        return LOW_SCORE;
    }

    private List<String> reasonCodesOf(final FraudPredictRequest request, final RiskLevel riskLevel) {
        final List<String> reasonCodes = new ArrayList<>();
        if (riskLevel != RiskLevel.LOW) {
            reasonCodes.add(REASON_HIGH_AMOUNT);
        }
        if (request.recipient().firstTime()) {
            reasonCodes.add(REASON_NEW_RECIPIENT);
        }
        if (request.profile().coldStart()) {
            reasonCodes.add(REASON_COLD_START);
        }
        return List.copyOf(reasonCodes);
    }
}
