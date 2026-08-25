package com.movi_backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movi.crypto")
public record CryptoProperties(
        String encryptionKey,
        String hashKey
) {
}
