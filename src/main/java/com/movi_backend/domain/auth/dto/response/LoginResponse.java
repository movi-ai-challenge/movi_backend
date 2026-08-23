package com.movi_backend.domain.auth.dto.response;

import com.movi_backend.global.security.JwtTokenPair;

public record LoginResponse(
        Long userId,
        boolean newUser,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn
) {

    public static LoginResponse of(
            final Long userId,
            final boolean newUser,
            final JwtTokenPair tokenPair
    ) {
        return new LoginResponse(
                userId,
                newUser,
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.tokenType(),
                tokenPair.accessTokenExpiresIn()
        );
    }
}
