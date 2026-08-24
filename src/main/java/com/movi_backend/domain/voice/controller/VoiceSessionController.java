package com.movi_backend.domain.voice.controller;

import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.application.VoiceCommandService;
import com.movi_backend.domain.voice.application.VoiceSessionService;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice/sessions")
@RequiredArgsConstructor
public class VoiceSessionController {

    private static final String START_VOICE_MESSAGE = "무엇을 도와드릴까요?";

    private final VoiceSessionService voiceSessionService;
    private final VoiceCommandService voiceCommandService;

    /** 인증된 사용자의 음성 세션을 시작하고 첫 음성 안내를 반환한다. */
    @PostMapping
    public ApiResponse<VoiceSessionStartResponse> start(
            @CurrentUser final AuthUser authUser
    ) {
        final VoiceSessionStartResponse response = voiceSessionService.start(authUser.userId());
        return ApiResponse.success(response, START_VOICE_MESSAGE);
    }

    /** 음성 파일을 분석해 이체 재질문 또는 최종 확인 응답을 반환한다. */
    @PostMapping(path = "/{voiceSessionId}/commands", consumes = "multipart/form-data")
    public ApiResponse<VoiceCommandResponse> command(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long voiceSessionId,
            @RequestPart("audio") final MultipartFile audio,
            @RequestPart(value = "confirmationId", required = false)
            final String confirmationId,
            @RequestPart(value = "idempotencyKey", required = false)
            final String idempotencyKey
    ) {
        final VoiceCommandResponse response = voiceCommandService.process(
                authUser.userId(),
                voiceSessionId,
                audio,
                confirmationId,
                idempotencyKey
        );
        throwIfTransferWasNotExecuted(response);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    private void throwIfTransferWasNotExecuted(final VoiceCommandResponse response) {
        if (response.status() == TransferStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.HIGH_RISK_BLOCKED);
        }
        if (response.status() == TransferStatus.FAILED) {
            throw new BusinessException(ErrorCode.TRANSFER_EXECUTION_FAILED);
        }
    }
}
