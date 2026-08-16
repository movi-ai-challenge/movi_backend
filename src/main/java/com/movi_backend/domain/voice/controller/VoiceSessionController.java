package com.movi_backend.domain.voice.controller;

import com.movi_backend.domain.voice.application.VoiceSessionService;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice/sessions")
@RequiredArgsConstructor
public class VoiceSessionController {

    private static final String START_VOICE_MESSAGE = "무엇을 도와드릴까요?";

    private final VoiceSessionService voiceSessionService;

    /** 인증된 사용자의 음성 세션을 시작하고 첫 음성 안내를 반환한다. */
    @PostMapping
    public ApiResponse<VoiceSessionStartResponse> start(
            @CurrentUser final AuthUser authUser
    ) {
        final VoiceSessionStartResponse response = voiceSessionService.start(authUser.userId());
        return ApiResponse.success(response, START_VOICE_MESSAGE);
    }
}
