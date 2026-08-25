package com.movi_backend.domain.auth.controller;

import com.movi_backend.domain.auth.application.KakaoLoginService;
import com.movi_backend.domain.auth.config.KakaoProperties;
import com.movi_backend.domain.auth.application.LoginHandoffStore;
import com.movi_backend.domain.auth.controller.docs.KakaoAuthApiDocs;
import com.movi_backend.domain.auth.dto.request.LoginCodeExchangeRequest;
import com.movi_backend.domain.auth.dto.response.KakaoAuthorization;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class KakaoAuthController implements KakaoAuthApiDocs {

    private static final String OAUTH_STATE_COOKIE = "KAKAO_OAUTH_STATE";
    private static final String AUTH_PATH = "/api/v1/auth";

    private final KakaoLoginService kakaoLoginService;
    private final LoginHandoffStore loginHandoffStore;
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
        final LoginHandoffStore.Handoff handoff =
                kakaoLoginService.authenticate(code, state, stateCookie);
        final String handoffCode = loginHandoffStore.issue(handoff.userId(), handoff.newUser());
        final ResponseCookie expiredStateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(kakaoProperties.secureCookie())
                .sameSite("Lax")
                .path(AUTH_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, buildFrontendRedirectUri(handoff, handoffCode).toString())
                .header(HttpHeaders.SET_COOKIE, expiredStateCookie.toString())
                .build();
    }

    /**
     * 프런트로 돌아갈 주소를 만든다.
     *
     * <p><b>토큰을 여기 싣지 않는다.</b> 이 주소는 브라우저 기록·프런트 호스트 로그·
     * {@code Referer} 헤더에 남는다. 교환 코드만 실어 보내고 토큰은 본문으로 넘긴다.
     */
    private URI buildFrontendRedirectUri(
            final LoginHandoffStore.Handoff handoff,
            final String handoffCode
    ) {
        final UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(requiredFrontendRedirectUri())
                        .queryParam("code", handoffCode)
                        .queryParam("newUser", handoff.newUser());
        if (kakaoProperties.legacyTokenQuery()) {
            appendLegacyTokenQuery(builder, handoff);
        }
        return builder.build().encode().toUri();
    }

    /**
     * 토큰을 URL 에 싣던 기존 방식.
     *
     * @deprecated 프런트가 교환 코드로 옮겨오면 {@code movi.kakao.legacy-token-query} 를
     *     false 로 바꾸고, 그 뒤 이 메서드와 설정을 함께 지운다.
     */
    @Deprecated
    private void appendLegacyTokenQuery(
            final UriComponentsBuilder builder,
            final LoginHandoffStore.Handoff handoff
    ) {
        final LoginResponse response =
                kakaoLoginService.issueTokens(handoff.userId(), handoff.newUser());
        builder.queryParam("accessToken", response.accessToken())
                .queryParam("refreshToken", response.refreshToken())
                .queryParam("userId", response.userId())
                .queryParam("tokenType", response.tokenType())
                .queryParam("accessTokenExpiresIn", response.accessTokenExpiresIn());
    }

    /** 일회성 코드를 토큰으로 바꾼다. 코드는 한 번만 쓰이고 60초 뒤 만료된다. */
    @PostMapping("/kakao/token")
    public ApiResponse<LoginResponse> exchange(
            @Valid @RequestBody final LoginCodeExchangeRequest request
    ) {
        final LoginHandoffStore.Handoff handoff = loginHandoffStore.consume(request.code());
        if (handoff == null) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN_CODE);
        }
        return ApiResponse.success(
                kakaoLoginService.issueTokens(handoff.userId(), handoff.newUser())
        );
    }

    private String requiredFrontendRedirectUri() {
        final String frontendRedirectUri = kakaoProperties.frontendRedirectUri();
        if (frontendRedirectUri == null || frontendRedirectUri.isBlank()) {
            throw new IllegalStateException("카카오 프론트엔드 Redirect URI 설정이 필요합니다.");
        }
        return frontendRedirectUri;
    }

}
