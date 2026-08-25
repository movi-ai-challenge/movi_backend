package com.movi_backend.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 오리진 설정.
 *
 * @param allowedOrigins 프론트엔드가 요청을 보낼 수 있는 오리진 패턴 목록.
 *                       와일드카드({@code *})를 쓸 수 있다. 값을 비워 두면
 *                       로컬 개발 서버와 배포된 프론트엔드 주소를 기본값으로 쓴다.
 */
@ConfigurationProperties(prefix = "movi.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "https://movi-ai-challenge.netlify.app",
            "https://*--movi-ai-challenge.netlify.app"
    );

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = DEFAULT_ALLOWED_ORIGINS;
        }
    }
}
