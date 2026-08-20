package com.movi_backend.domain.fds.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movi.fds")
public record FdsClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration responseTimeout
) {

    private static final String DEFAULT_BASE_URL = "http://localhost:8000";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(3);

    public FdsClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (responseTimeout == null) {
            responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("FDS API 연결 제한 시간은 0보다 커야 합니다.");
        }
        if (responseTimeout.isNegative() || responseTimeout.isZero()) {
            throw new IllegalArgumentException("FDS API 응답 제한 시간은 0보다 커야 합니다.");
        }
    }
}
