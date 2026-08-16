package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record FdsContextFeature(
        boolean trustedDevice,
        BigDecimal sttConfidence
) {

    public FdsContextFeature {
        Objects.requireNonNull(sttConfidence, "sttConfidence는 필수입니다.");
        if (sttConfidence.compareTo(BigDecimal.ZERO) < 0
                || sttConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("sttConfidence는 0부터 1 사이여야 합니다.");
        }
    }

    public static FdsContextFeature of(
            final boolean trustedDevice,
            final BigDecimal sttConfidence
    ) {
        return new FdsContextFeature(trustedDevice, sttConfidence);
    }
}
