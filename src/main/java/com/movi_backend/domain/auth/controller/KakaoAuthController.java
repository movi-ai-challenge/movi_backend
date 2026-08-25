package com.movi_backend.domain.auth.controller;

import com.movi_backend.domain.auth.application.KakaoLoginService;
import com.movi_backend.domain.auth.config.KakaoProperties;
import com.movi_backend.domain.auth.controller.docs.KakaoAuthApiDocs;
import com.movi_backend.domain.auth.dto.response.KakaoAuthorization;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.global.response.ApiResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class KakaoAuthController implements KakaoAuthApiDocs {

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
    public ResponseEntity<ApiResponse<LoginResponse>> callback(
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
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredStateCookie.toString())
                .body(ApiResponse.success(response, "카카오 로그인이 완료됐어요."));
    }

}
