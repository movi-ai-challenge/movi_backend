package com.movi_backend.domain.auth.controller;

import com.movi_backend.domain.auth.application.AuthenticationService;
import com.movi_backend.domain.auth.controller.docs.AuthApiDocs;
import com.movi_backend.domain.auth.dto.request.PasswordLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinRegisterRequest;
import com.movi_backend.domain.auth.dto.request.SignUpRequest;
import com.movi_backend.domain.auth.dto.request.TokenRefreshRequest;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import com.movi_backend.global.security.JwtTokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {

    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signUp(@Valid @RequestBody final SignUpRequest request) {
        final LoginResponse response = authenticationService.signUp(request);
        return ApiResponse.success(response, "가입이 끝났어요. 바로 시작할 수 있어요.");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody final PasswordLoginRequest request) {
        final LoginResponse response = authenticationService.loginWithPassword(request);
        return ApiResponse.success(response, "로그인이 완료됐어요.");
    }

    @PostMapping("/pin/login")
    public ApiResponse<LoginResponse> loginWithPin(@Valid @RequestBody final PinLoginRequest request) {
        final LoginResponse response = authenticationService.loginWithPin(request);
        return ApiResponse.success(response, "로그인이 완료됐어요.");
    }

    @PostMapping("/pin/register")
    public ApiResponse<Void> registerPin(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final PinRegisterRequest request
    ) {
        authenticationService.registerPin(authUser.userId(), request);
        return ApiResponse.successWithVoice("비밀번호를 등록했어요.");
    }

    @PostMapping("/token/refresh")
    public ApiResponse<JwtTokenPair> refresh(@Valid @RequestBody final TokenRefreshRequest request) {
        final JwtTokenPair tokenPair = authenticationService.refresh(request.refreshToken());
        return ApiResponse.success(tokenPair);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CurrentUser final AuthUser authUser) {
        authenticationService.logout(authUser.userId());
        return ApiResponse.successWithVoice("로그아웃했어요.");
    }
}
