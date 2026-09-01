package com.movi_backend.domain.auth.dto.response;

import com.movi_backend.global.security.JwtTokenPair;

/**
 * 로그인·회원가입 응답.
 *
 * <p>{@code name}을 함께 내리는 이유는 첫 화면이 "OOO님"으로 사용자를 부르기 때문이다.
 * 이 값이 없으면 프런트가 로그인 수단으로 이름을 지어내야 한다("카카오로 로그인한 사용자").
 * 화면을 보지 않는 사용자에게 TTS로 읽히는 문장이라, 자기 이름이 아니면 어색하다.
 */
public record LoginResponse(
        Long userId,
        String name,
        boolean newUser,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn
) {

    public static LoginResponse of(
            final Long userId,
            final String name,
            final boolean newUser,
            final JwtTokenPair tokenPair
    ) {
        return new LoginResponse(
                userId,
                name,
                newUser,
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.tokenType(),
                tokenPair.accessTokenExpiresIn()
        );
    }
}
