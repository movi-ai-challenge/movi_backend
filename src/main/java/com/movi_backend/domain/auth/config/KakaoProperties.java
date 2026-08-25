package com.movi_backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "movi.kakao")
public record KakaoProperties(
        String restApiKey,
        String clientSecret,
        String redirectUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        Boolean secureCookie,
        String frontendRedirectUri,
        Boolean legacyTokenQuery
) {

    private static final String DEFAULT_AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String DEFAULT_TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String DEFAULT_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    public KakaoProperties {
        authorizationUri = defaultIfBlank(authorizationUri, DEFAULT_AUTHORIZATION_URI);
        tokenUri = defaultIfBlank(tokenUri, DEFAULT_TOKEN_URI);
        userInfoUri = defaultIfBlank(userInfoUri, DEFAULT_USER_INFO_URI);
        if (secureCookie == null) {
            secureCookie = true;
        }
        // 프런트가 교환 코드로 옮겨오기 전까지는 기존 쿼리 방식도 함께 내보낸다.
        // 옮겨온 뒤 false 로 바꾸면 리다이렉트 URL 에서 토큰이 사라진다.
        if (legacyTokenQuery == null) {
            legacyTokenQuery = true;
        }
    }

    public boolean hasClientSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    private static String defaultIfBlank(final String value, final String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
