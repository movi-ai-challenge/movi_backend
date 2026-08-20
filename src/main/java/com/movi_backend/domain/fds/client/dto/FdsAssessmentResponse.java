package com.movi_backend.domain.fds.client.dto;

import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.util.List;

public record FdsAssessmentResponse(
        String requestId,
        String modelVersion,
        String policyVersion,
        FdsScores scores,
        RiskLevel riskLevel,
        FdsDecision decision,
        List<String> reasonCodes,
        Integer latencyMs
) {

    public FdsAssessmentResponse {
        if (reasonCodes != null) {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    public static FdsAssessmentResponse of(
            final String requestId,
            final String modelVersion,
            final String policyVersion,
            final FdsScores scores,
            final RiskLevel riskLevel,
            final FdsDecision decision,
            final List<String> reasonCodes,
            final Integer latencyMs
    ) {
        return new FdsAssessmentResponse(
                requestId,
                modelVersion,
                policyVersion,
                scores,
                riskLevel,
                decision,
                reasonCodes,
                latencyMs
        );
    }
}
