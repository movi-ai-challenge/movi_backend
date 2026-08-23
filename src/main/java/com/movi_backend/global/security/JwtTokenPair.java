package com.movi_backend.global.security;

public record JwtTokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn
) {

    private static final String BEARER = "Bearer";

    public static JwtTokenPair of(
            final String accessToken,
            final String refreshToken,
            final long accessTokenExpiresIn
    ) {
        return new JwtTokenPair(accessToken, refreshToken, BEARER, accessTokenExpiresIn);
    }
}
