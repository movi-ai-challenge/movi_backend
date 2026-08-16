package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;
import java.util.List;

public record FdsProfileFeature(
        boolean coldStart,
        BigDecimal averageAmount30d,
        BigDecimal maximumAmount30d,
        BigDecimal stddevAmount30d,
        int transferCount30d,
        int distinctRecipients30d,
        List<Integer> commonHours
) {

    public FdsProfileFeature {
        if (transferCount30d < 0 || distinctRecipients30d < 0) {
            throw new IllegalArgumentException("프로필 횟수는 음수일 수 없습니다.");
        }
        if (commonHours == null) {
            commonHours = List.of();
        } else {
            commonHours = List.copyOf(commonHours);
        }
        validateCommonHours(commonHours);
        validateColdStart(
                coldStart,
                averageAmount30d,
                maximumAmount30d,
                stddevAmount30d,
                transferCount30d,
                distinctRecipients30d,
                commonHours
        );
        validateAmount(averageAmount30d);
        validateAmount(maximumAmount30d);
        validateAmount(stddevAmount30d);
    }

    public static FdsProfileFeature coldStartProfile() {
        return new FdsProfileFeature(true, null, null, null, 0, 0, List.of());
    }

    public static FdsProfileFeature of(
            final BigDecimal averageAmount30d,
            final BigDecimal maximumAmount30d,
            final BigDecimal stddevAmount30d,
            final int transferCount30d,
            final int distinctRecipients30d,
            final List<Integer> commonHours
    ) {
        return new FdsProfileFeature(
                false,
                averageAmount30d,
                maximumAmount30d,
                stddevAmount30d,
                transferCount30d,
                distinctRecipients30d,
                commonHours
        );
    }

    private static void validateCommonHours(final List<Integer> commonHours) {
        if (commonHours.stream().anyMatch(hour -> hour == null || hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("주 이체 시간은 0시부터 23시까지여야 합니다.");
        }
    }

    private static void validateColdStart(
            final boolean coldStart,
            final BigDecimal averageAmount30d,
            final BigDecimal maximumAmount30d,
            final BigDecimal stddevAmount30d,
            final int transferCount30d,
            final int distinctRecipients30d,
            final List<Integer> commonHours
    ) {
        if (!coldStart) {
            return;
        }
        if (averageAmount30d != null || maximumAmount30d != null || stddevAmount30d != null
                || transferCount30d != 0 || distinctRecipients30d != 0
                || !commonHours.isEmpty()) {
            throw new IllegalArgumentException("초기 사용자 프로필은 집계값이 비어 있어야 합니다.");
        }
    }

    private static void validateAmount(final BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("프로필 금액은 음수일 수 없습니다.");
        }
    }
}
