package com.movi_backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 관련 설정.
 *
 * @param devMode      true면 인증 없이 {@code X-Dev-User-Id} 헤더로 사용자를 지정할 수 있다.
 *                     JWT 구현 전 다른 파트가 API를 개발·테스트하기 위한 장치다.
 *                     <b>운영 환경에서는 반드시 false여야 한다.</b>
 * @param devUserId    devMode에서 헤더가 없을 때 사용할 기본 사용자 ID
 */
@ConfigurationProperties(prefix = "movi.auth")
public record AuthProperties(
        boolean devMode,
        Long devUserId
) {
    public AuthProperties {
        if (devUserId == null) {
            devUserId = 1L;
        }
    }
}
