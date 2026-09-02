package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * FDS 평가에 필요한 특징(feature) 요청.
 *
 * <p>{@code fromFintechUseNum}·{@code fromBankCode}·{@code toBankCode}·
 * {@code toAccountNumEncrypted}는 실제 오픈뱅킹 이체({@code OpenBankingTransferPort})에 넘기는
 * 값과 같은 것이다. {@link com.movi_backend.domain.fds.client.MockFdsAssessmentClient}는 쓰지
 * 않고, 실제 AI 서버가 요구하는 원시 거래 스키마(계좌·은행)를 만들 때만 쓴다 — 그 값이 필요한
 * 것은 HTTP 어댑터뿐이므로, 어댑터가 {@code Transfer}를 다시 조회하는 대신 호출부가 이미 들고
 * 있는 값을 여기 실어 보낸다. {@code toAccountNumEncrypted}는 암호문 그대로 두고, 복호화는
 * 실제로 보낼 때(HTTP 어댑터 안)에서만 한다.
 */
public record FdsAssessmentRequest(
        String requestId,
        Long transferId,
        Long userId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        OffsetDateTime requestedAt,
        FdsRecipientFeature recipient,
        FdsProfileFeature profile,
        FdsContextFeature context,
        String fromFintechUseNum,
        String fromBankCode,
        String toBankCode,
        String toAccountNumEncrypted
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
        requireNotBlank(fromFintechUseNum, "fromFintechUseNum");
        requireNotBlank(fromBankCode, "fromBankCode");
        requireNotBlank(toBankCode, "toBankCode");
        requireNotBlank(toAccountNumEncrypted, "toAccountNumEncrypted");
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
            final FdsContextFeature context,
            final String fromFintechUseNum,
            final String fromBankCode,
            final String toBankCode,
            final String toAccountNumEncrypted
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
                context,
                fromFintechUseNum,
                fromBankCode,
                toBankCode,
                toAccountNumEncrypted
        );
    }

    private static void requireNotBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
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
