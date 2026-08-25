package com.movi_backend.domain.guardian.controller;

import com.movi_backend.domain.guardian.application.GuardianLinkService;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 등록 API.
 *
 * <p>회원가입 온보딩에서 보호자 전화번호를 입력하면 이 API가 확인 절차 없이 바로 연결을
 * {@code ACTIVE}로 만든다. 로그인한 본인(피보호자) 계정으로만 호출할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardian-links")
public class GuardianLinkController {

    private final GuardianLinkService guardianLinkService;

    /** 7.1 보호자 등록 */
    @PostMapping
    public ApiResponse<GuardianLinkRegisterResponse> register(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final GuardianLinkCreateRequest request
    ) {
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(authUser.userId(), request);
        return ApiResponse.success(response, response.toVoiceMessage());
    }
}
