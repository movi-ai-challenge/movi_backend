package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsContextFeature;
import com.movi_backend.domain.fds.client.dto.FdsHistoryEntry;
import com.movi_backend.domain.fds.client.dto.FdsProfileFeature;
import com.movi_backend.domain.fds.client.dto.FdsRecipientFeature;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

final class FdsClientFixture {

    private FdsClientFixture() {
    }

    static FdsAssessmentRequest normalRequest() {
        return requestOf(
                new BigDecimal("50000"),
                FdsRecipientFeature.of(5, false),
                FdsProfileFeature.of(
                        new BigDecimal("42000"),
                        new BigDecimal("100000"),
                        new BigDecimal("11000"),
                        8,
                        3,
                        List.of(9, 12, 18)
                ),
                FdsContextFeature.of(true, new BigDecimal("0.93"))
        );
    }

    static FdsAssessmentRequest normalRequestWithRecipientAccount(final String toAccountNumEncrypted) {
        return FdsAssessmentRequest.of(
                "fds-transfer-101",
                101L,
                3L,
                new BigDecimal("50000"),
                new BigDecimal("320000"),
                OffsetDateTime.of(2026, 8, 14, 14, 30, 0, 0, ZoneOffset.ofHours(9)),
                FdsRecipientFeature.of(5, false),
                FdsProfileFeature.of(
                        new BigDecimal("42000"),
                        new BigDecimal("100000"),
                        new BigDecimal("11000"),
                        8,
                        3,
                        List.of(9, 12, 18)
                ),
                FdsContextFeature.of(true, new BigDecimal("0.93")),
                "199000000000000000000001",
                "004",
                "088",
                toAccountNumEncrypted,
                List.of()
        );
    }

    /** 과거 출금 {@code count}건. 금액·일시는 서로 다르게 둬 이력이 그대로 실리는지 본다. */
    static List<FdsHistoryEntry> historyOf(final int count, final String counterpartyEncrypted) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> FdsHistoryEntry.of(
                        new BigDecimal(String.valueOf(10000 * index)),
                        OffsetDateTime.of(2026, 8, index, 13, 0, 0, 0, ZoneOffset.ofHours(9)),
                        counterpartyEncrypted
                ))
                .toList();
    }

    static FdsAssessmentRequest requestWithHistory(
            final String toAccountNumEncrypted,
            final List<FdsHistoryEntry> history
    ) {
        return FdsAssessmentRequest.of(
                "fds-transfer-101",
                101L,
                3L,
                new BigDecimal("50000"),
                new BigDecimal("320000"),
                OffsetDateTime.of(2026, 8, 14, 14, 30, 0, 0, ZoneOffset.ofHours(9)),
                FdsRecipientFeature.of(5, false),
                FdsProfileFeature.of(
                        new BigDecimal("42000"),
                        new BigDecimal("100000"),
                        new BigDecimal("11000"),
                        8,
                        3,
                        List.of(9, 12, 18)
                ),
                FdsContextFeature.of(true, new BigDecimal("0.93")),
                "199000000000000000000001",
                "004",
                "088",
                toAccountNumEncrypted,
                history
        );
    }

    static FdsAssessmentRequest requestOf(
            final BigDecimal amount,
            final FdsRecipientFeature recipient,
            final FdsProfileFeature profile,
            final FdsContextFeature context
    ) {
        return FdsAssessmentRequest.of(
                "fds-transfer-101",
                101L,
                3L,
                amount,
                new BigDecimal("320000"),
                OffsetDateTime.of(2026, 8, 14, 14, 30, 0, 0, ZoneOffset.ofHours(9)),
                recipient,
                profile,
                context,
                "199000000000000000000001",
                "004",
                "088",
                "encrypted-recipient-account-num",
                List.of()
        );
    }

    static FdsAssessmentResponse responseOf(
            final String requestId,
            final FdsScores scores,
            final RiskLevel riskLevel,
            final FdsDecision decision,
            final Integer latencyMs
    ) {
        return FdsAssessmentResponse.of(
                requestId,
                "isolation-forest-v1",
                "risk-policy-v1",
                scores,
                riskLevel,
                decision,
                List.of("NEW_RECIPIENT"),
                latencyMs
        );
    }
}
