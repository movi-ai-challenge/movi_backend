package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.TransferFacade;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.response.TransferResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이체 API.
 *
 * <p>고위험으로 감지된 이체도 HTTP 200으로 응답한다. 요청 처리 자체는 정상적으로 끝났고,
 * 사용자가 알아야 할 것은 "정말 보낼지 다시 묻고 있다"는 사실이기 때문이다. 결과는
 * {@code data.status}(HOLD)와 {@code voiceMessage}로 구분한다.
 *
 * <p>확인 응답은 {@code confirm}/{@code decline} 두 엔드포인트로 받는다. 음성에서 "네"와
 * "아니요"를 구분하는 일은 AI 파트가 하고, <b>실제 실행 판단은 백엔드가 한다.</b>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private static final String DECLINE_VOICE_MESSAGE = "송금을 취소했어요.";

    private final TransferFacade transferFacade;

    /** 이체 요청. 고위험이면 실행하지 않고 확인 대기 상태로 응답한다. */
    @PostMapping
    public ApiResponse<TransferResponse> execute(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final TransferExecuteRequest request
    ) {
        final TransferResponse response = transferFacade.execute(authUser.userId(), request);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    /** 고위험 이체를 본인이 재확인했다. 이 요청을 받아야 실제 송금이 나간다. */
    @PostMapping("/{transferId}/confirm")
    public ApiResponse<TransferResponse> confirm(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long transferId
    ) {
        final TransferResponse response = transferFacade.confirm(authUser.userId(), transferId);
        return ApiResponse.success(response, response.toVoiceMessage());
    }

    /**
     * 고위험 이체를 본인이 거절했다.
     *
     * <p>안내 문구를 {@code toVoiceMessage()}에 맡기지 않는다. 같은 {@code BLOCKED}라도
     * 사용자가 직접 취소한 것과 시스템이 막은 것은 들려줄 말이 다르다.
     */
    @PostMapping("/{transferId}/decline")
    public ApiResponse<TransferResponse> decline(
            @CurrentUser final AuthUser authUser,
            @PathVariable final Long transferId
    ) {
        final TransferResponse response = transferFacade.decline(authUser.userId(), transferId);
        return ApiResponse.success(response, DECLINE_VOICE_MESSAGE);
    }
}
