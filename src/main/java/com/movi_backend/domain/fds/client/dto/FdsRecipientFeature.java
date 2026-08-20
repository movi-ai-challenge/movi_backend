package com.movi_backend.domain.fds.client.dto;

public record FdsRecipientFeature(
        int transferCount,
        boolean firstTime
) {

    public FdsRecipientFeature {
        if (transferCount < 0) {
            throw new IllegalArgumentException("수취인 이체 횟수는 음수일 수 없습니다.");
        }
        if (firstTime != (transferCount == 0)) {
            throw new IllegalArgumentException("최초 수취인 여부와 이체 횟수가 일치하지 않습니다.");
        }
    }

    public static FdsRecipientFeature of(
            final int transferCount,
            final boolean firstTime
    ) {
        return new FdsRecipientFeature(transferCount, firstTime);
    }
}
