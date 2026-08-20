package com.movi_backend.domain.auth.dto.response;

import java.net.URI;

public record KakaoAuthorization(
        URI authorizationUri,
        String state
) {

    public static KakaoAuthorization of(final URI authorizationUri, final String state) {
        return new KakaoAuthorization(authorizationUri, state);
    }
}
