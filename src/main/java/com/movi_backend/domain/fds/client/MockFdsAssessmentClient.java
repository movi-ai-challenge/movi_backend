package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "movi.fds",
        name = "client-type",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockFdsAssessmentClient implements FdsAssessmentClient {

    private static final BigDecimal MEDIUM_AMOUNT = new BigDecimal("100000");
    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("700000");

    @Override
    public FdsAssessmentResponse assess(final FdsAssessmentRequest request) {
        final RiskLevel riskLevel = determineRiskLevel(request);
        return createResponse(request, riskLevel);
    }

    private RiskLevel determineRiskLevel(final FdsAssessmentRequest request) {
        if (request.amount().compareTo(HIGH_AMOUNT) >= 0) {
            return RiskLevel.HIGH;
        }
        if (request.recipient().firstTime()
                || request.profile().coldStart()
                || !request.context().trustedDevice()
                || request.amount().compareTo(MEDIUM_AMOUNT) > 0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private FdsAssessmentResponse createResponse(
            final FdsAssessmentRequest request,
            final RiskLevel riskLevel
    ) {
        if (riskLevel == RiskLevel.HIGH) {
            return responseOf(
                    request,
                    riskLevel,
                    new BigDecimal("0.91"),
                    List.of("HIGH_AMOUNT")
            );
        }
        if (riskLevel == RiskLevel.MEDIUM) {
            return responseOf(
                    request,
                    riskLevel,
                    new BigDecimal("0.65"),
                    determineMediumReasonCodes(request)
            );
        }
        return responseOf(
                request,
                riskLevel,
                new BigDecimal("0.15"),
                List.of()
        );
    }

    private FdsAssessmentResponse responseOf(
            final FdsAssessmentRequest request,
            final RiskLevel riskLevel,
            final BigDecimal score,
            final List<String> reasonCodes
    ) {
        return FdsAssessmentResponse.of(
                request.requestId(),
                "mock-isolation-forest-v1",
                "mock-risk-policy-v1",
                FdsScores.of(score, score, score),
                riskLevel,
                FdsDecision.from(riskLevel),
                reasonCodes,
                1
        );
    }

    private List<String> determineMediumReasonCodes(final FdsAssessmentRequest request) {
        final List<String> reasonCodes = new ArrayList<>();
        if (request.recipient().firstTime()) {
            reasonCodes.add("NEW_RECIPIENT");
        }
        if (request.profile().coldStart()) {
            reasonCodes.add("COLD_START");
        }
        if (!request.context().trustedDevice()) {
            reasonCodes.add("NEW_DEVICE");
        }
        if (request.amount().compareTo(MEDIUM_AMOUNT) > 0) {
            reasonCodes.add("HIGH_AMOUNT");
        }
        return List.copyOf(reasonCodes);
    }
}
