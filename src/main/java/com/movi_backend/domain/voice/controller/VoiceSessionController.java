package com.movi_backend.domain.voice.controller;

import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.application.VoiceCommandResultStore;
import com.movi_backend.domain.voice.application.VoiceCommandService;
import com.movi_backend.domain.voice.application.VoiceSessionService;
import com.movi_backend.domain.voice.controller.docs.VoiceSessionApiDocs;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.dto.request.VoiceSessionStartRequest;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice/sessions")
@RequiredArgsConstructor
public class VoiceSessionController implements VoiceSessionApiDocs {

    private static final String START_VOICE_MESSAGE = "무엇을 도와드릴까요?";

    private final VoiceSessionService voiceSessionService;
    private final VoiceCommandService voiceCommandService;
    private final VoiceCommandResultStore voiceCommandResultStore;

    /** 인증된 사용자의 음성 세션을 시작하고 첫 음성 안내를 반환한다. */
    @PostMapping
    public ApiResponse<VoiceSessionStartResponse> start(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody(required = false) final VoiceSessionStartRequest request
    ) {
        final VoiceSessionStartResponse response = voiceSessionService.start(
                authUser.userId(),
                readDeviceUuid(request)
        );
        return ApiResponse.success(response, START_VOICE_MESSAGE);
    }

    /** 본문 없이 호출하던 기존 클라이언트를 그대로 받는다. */
    private String readDeviceUuid(final VoiceSessionStartRequest request) {
        if (request == null) {
            return null;
        }
        return request.deviceUuid();
    }

    /**
     * 세션의 마지막 응답을 다시 가져온다.
     *
     * <p>스트리밍 응답은 마지막 한 프레임이 도착해야만 성공한다. 그 프레임을 놓치면 답이
     * 서버에 멀쩡히 있어도 사용자는 알 방법이 없다. 화면을 보지 않는 사용자에게는 다시
     * 말하는 것 말고 방법이 없고, 같은 지점에서 또 막힌다.
     *
     * <p>전달이 실패할 수 있다는 것을 받아들이고, 다시 물어볼 길을 둔다.
     */
    @GetMapping("/{voiceSessionId}/result")
    public ApiResponse<VoiceCommandResponse> getLastResult(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long voiceSessionId
    ) {
        final VoiceCommandResultStore.StoredResult stored = voiceCommandResultStore.find(
                authUser.userId(),
                voiceSessionId
        );
        if (stored == null) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        return ApiResponse.success(stored.response(), stored.voiceMessage());
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
