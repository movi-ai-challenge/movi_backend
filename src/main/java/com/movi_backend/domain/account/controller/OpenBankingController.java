package com.movi_backend.domain.account.controller;

import com.movi_backend.domain.account.application.OpenBankingConnectService;
import com.movi_backend.domain.account.controller.docs.OpenBankingApiDocs;
import com.movi_backend.domain.account.dto.response.ConnectResultResponse;
import com.movi_backend.domain.account.dto.response.ConnectStartResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오픈뱅킹 계좌 연결 (명세서 1.1, 1.2).
 */
@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
public class OpenBankingController implements OpenBankingApiDocs {

    private static final String START_VOICE_MESSAGE = "은행 계좌를 연결할게요. 화면 안내를 따라 주세요.";

    private final OpenBankingConnectService connectService;

    /** 계좌 연결을 시작한다. 반환된 URL로 사용자를 보낸다. */
    @PostMapping("/connect")
    public ApiResponse<ConnectStartResponse> startConnect(@CurrentUser final AuthUser authUser) {
        final String url = connectService.startConnect(authUser.userId());
        return ApiResponse.success(ConnectStartResponse.of(url), START_VOICE_MESSAGE);
    }

    /**
     * 오픈뱅킹이 인가 코드를 돌려주는 콜백.
     *
     * <p>인증되지 않은 요청으로 들어오므로 {@code state}가 유일한 신원 증명이다.
     * 서비스에서 대조하고 즉시 폐기한다.
     */
    @GetMapping("/callback")
    public ApiResponse<ConnectResultResponse> callback(
            @RequestParam("code") final String code,
            @RequestParam("state") final String state
    ) {
        final ConnectResultResponse response = connectService.completeConnect(code, state);
        return ApiResponse.success(response, response.toVoiceMessage());
    }
}
