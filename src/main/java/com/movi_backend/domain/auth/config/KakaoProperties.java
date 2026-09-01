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
        // 프런트가 교환 코드(POST /api/v1/auth/kakao/token)로 옮겨왔으므로 기본값을 껐다.
        // 리다이렉트 URL 은 브라우저 기록·프런트 호스트 로그·Referer 헤더에 남는다.
        // 되돌려야 할 일이 생기면 movi.kakao.legacy-token-query=true 로 잠시 켠다.
        if (legacyTokenQuery == null) {
            legacyTokenQuery = false;
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
