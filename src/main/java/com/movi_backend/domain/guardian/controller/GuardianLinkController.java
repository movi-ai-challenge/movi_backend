package com.movi_backend.domain.guardian.controller;

import com.movi_backend.domain.guardian.application.GuardianLinkService;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianInvitationResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkApprovalResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRejectionResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRequestResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 연결 API.
 *
 * <p>초대 확인({@code GET /invitations/{inviteToken}})만 인증 없이 열려 있다. 보호자가 아직
 * 가입 전일 수 있어 로그인을 먼저 요구하면 무엇에 동의하는지 모른 채 가입하게 된다.
 * 승인·거절은 로그인한 계정으로만 할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardian-links")
public class GuardianLinkController {

    private final GuardianLinkService guardianLinkService;

    /** 7.1 보호자 등록 요청 */
    @PostMapping
    public ApiResponse<GuardianLinkRequestResponse> request(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final GuardianLinkCreateRequest request
    ) {
        final GuardianLinkRequestResponse response =
                guardianLinkService.request(authUser.userId(), request);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    /** 7.2 초대 내용 확인 */
    @GetMapping("/invitations/{inviteToken}")
    public ApiResponse<GuardianInvitationResponse> findInvitation(
            @PathVariable final String inviteToken
    ) {
        final GuardianInvitationResponse response = guardianLinkService.findInvitation(inviteToken);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    /** 7.3 연결 승인 */
    @PostMapping("/invitations/{inviteToken}/approve")
    public ApiResponse<GuardianLinkApprovalResponse> approve(
            @CurrentUser final AuthUser authUser,
            @PathVariable final String inviteToken
    ) {
        final GuardianLinkApprovalResponse response =
                guardianLinkService.approve(authUser.userId(), inviteToken);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    /** 7.3 연결 거절 */
    @PostMapping("/invitations/{inviteToken}/reject")
    public ApiResponse<GuardianLinkRejectionResponse> reject(
            @CurrentUser final AuthUser authUser,
            @PathVariable final String inviteToken
    ) {
        final GuardianLinkRejectionResponse response =
                guardianLinkService.reject(authUser.userId(), inviteToken);
        return ApiResponse.success(response, response.toVoiceMessage());
    }
}
