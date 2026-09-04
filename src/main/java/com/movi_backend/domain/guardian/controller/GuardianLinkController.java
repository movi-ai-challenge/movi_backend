package com.movi_backend.domain.guardian.controller;

import com.movi_backend.domain.guardian.application.GuardianLinkService;
import com.movi_backend.domain.guardian.controller.docs.GuardianLinkApiDocs;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 등록 API.
 *
 * <p>로그인(카카오·PIN) 후 본인 계정에만 보호자를 붙일 수 있다. 등록되면 그 시점부터
 * 고위험·중위험 이체가 감지될 때 보호자 번호로 경고 문자가 나간다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardian-links")
public class GuardianLinkController implements GuardianLinkApiDocs {

    private final GuardianLinkService guardianLinkService;

    @GetMapping
    public ApiResponse<List<GuardianLinkRegisterResponse>> findActiveLinks(
            @CurrentUser final AuthUser authUser
    ) {
        return ApiResponse.success(guardianLinkService.findActiveLinks(authUser.userId()));
    }

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
