package com.movi_backend.domain.guardian.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movi.notification.retry")
public record NotificationRetryProperties(
        int maxAttempts,
        Duration delay,
        int batchSize
) {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_DELAY = Duration.ofMinutes(1);
    private static final int DEFAULT_BATCH_SIZE = 100;

    public NotificationRetryProperties {
        if (maxAttempts == 0) {
            maxAttempts = DEFAULT_MAX_ATTEMPTS;
        }
        if (delay == null) {
            delay = DEFAULT_DELAY;
        }
        if (batchSize == 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("알림 최대 발송 횟수는 1 이상이어야 합니다.");
        }
        if (delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("알림 재시도 지연은 0보다 커야 합니다.");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("알림 재시도 배치 크기는 1 이상이어야 합니다.");
        }
    }
}
