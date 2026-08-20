package com.movi_backend.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movi.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        Duration oauthStateExpiration
) {

    private static final Duration DEFAULT_ACCESS_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration DEFAULT_REFRESH_EXPIRATION = Duration.ofDays(14);
    private static final Duration DEFAULT_STATE_EXPIRATION = Duration.ofMinutes(5);

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "movi-backend";
        }
        if (accessTokenExpiration == null) {
            accessTokenExpiration = DEFAULT_ACCESS_EXPIRATION;
        }
        if (refreshTokenExpiration == null) {
            refreshTokenExpiration = DEFAULT_REFRESH_EXPIRATION;
        }
        if (oauthStateExpiration == null) {
            oauthStateExpiration = DEFAULT_STATE_EXPIRATION;
        }
    }
}
