package com.movi_backend.domain.auth.controller;

import com.movi_backend.domain.auth.application.KakaoLoginService;
import com.movi_backend.domain.auth.config.KakaoProperties;
import com.movi_backend.domain.auth.dto.response.KakaoAuthorization;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class KakaoAuthController {

    private static final String OAUTH_STATE_COOKIE = "KAKAO_OAUTH_STATE";
    private static final String AUTH_PATH = "/api/v1/auth";

    private final KakaoLoginService kakaoLoginService;
    private final KakaoProperties kakaoProperties;

    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> authorize() {
        final KakaoAuthorization authorization = kakaoLoginService.createAuthorization();
        final ResponseCookie stateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, authorization.state())
                .httpOnly(true)
                .secure(kakaoProperties.secureCookie())
                .sameSite("Lax")
                .path(AUTH_PATH)
                .maxAge(300)
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorization.authorizationUri().toString())
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) final String code,
            @RequestParam(required = false) final String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) final String stateCookie
    ) {
        final LoginResponse response = kakaoLoginService.login(code, state, stateCookie);
        final ResponseCookie expiredStateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(kakaoProperties.secureCookie())
                .sameSite("Lax")
                .path(AUTH_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, buildFrontendRedirectUri(response).toString())
                .header(HttpHeaders.SET_COOKIE, expiredStateCookie.toString())
                .build();
    }

    private URI buildFrontendRedirectUri(final LoginResponse response) {
        return UriComponentsBuilder.fromUriString(requiredFrontendRedirectUri())
                .queryParam("accessToken", response.accessToken())
                .queryParam("refreshToken", response.refreshToken())
                .queryParam("userId", response.userId())
                .queryParam("newUser", response.newUser())
                .queryParam("tokenType", response.tokenType())
                .queryParam("accessTokenExpiresIn", response.accessTokenExpiresIn())
                .build()
                .encode()
                .toUri();
    }

    private String requiredFrontendRedirectUri() {
        final String frontendRedirectUri = kakaoProperties.frontendRedirectUri();
        if (frontendRedirectUri == null || frontendRedirectUri.isBlank()) {
            throw new IllegalStateException("카카오 프론트엔드 Redirect URI 설정이 필요합니다.");
        }
        return frontendRedirectUri;
    }

}
