package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record FdsAssessmentRequest(
        String requestId,
        Long transferId,
        Long userId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        OffsetDateTime requestedAt,
        FdsRecipientFeature recipient,
        FdsProfileFeature profile,
        FdsContextFeature context
) {

    public FdsAssessmentRequest {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId는 필수입니다.");
        }
        Objects.requireNonNull(transferId, "transferId는 필수입니다.");
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        requirePositive(amount, "amount");
        requireNotNegative(balanceBefore, "balanceBefore");
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다.");
        Objects.requireNonNull(recipient, "recipient는 필수입니다.");
        Objects.requireNonNull(profile, "profile은 필수입니다.");
        Objects.requireNonNull(context, "context는 필수입니다.");
    }

    public static FdsAssessmentRequest of(
            final String requestId,
            final Long transferId,
            final Long userId,
            final BigDecimal amount,
            final BigDecimal balanceBefore,
            final OffsetDateTime requestedAt,
            final FdsRecipientFeature recipient,
            final FdsProfileFeature profile,
            final FdsContextFeature context
    ) {
        return new FdsAssessmentRequest(
                requestId,
                transferId,
                userId,
                amount,
                balanceBefore,
                requestedAt,
                recipient,
                profile,
                context
        );
    }

    private static void requirePositive(final BigDecimal value, final String fieldName) {
        requireNotNegative(value, fieldName);
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다.");
        }
    }

    private static void requireNotNegative(final BigDecimal value, final String fieldName) {
        Objects.requireNonNull(value, fieldName + "는 필수입니다.");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + "는 음수일 수 없습니다.");
        }
    }
}
