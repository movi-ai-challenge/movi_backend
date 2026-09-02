package com.movi_backend.domain.voice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI Voice API 의 내부 오류 외피.
 *
 * <p>계약({@code docs/ai-api-contract.md} 1절)이 정한 형태는 {@code {requestId, error}} 다.
 * 다만 AI 는 FastAPI 의 {@code HTTPException} 으로 내보내고 있어 실제로는 한 겹 더 감싸인
 * {@code {"detail": {requestId, error}}} 로 도착한다. 두 형태를 모두 읽는다 — 어느 쪽으로
 * 바뀌어도 백엔드가 코드를 잃지 않아야 한다.
 *
 * <p>{@code message} 는 사용자에게 그대로 전달하지 않는다. 백엔드가 {@code ErrorCode} 의
 * {@code voiceMessage} 로 바꾼다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoiceAnalysisErrorResponse(
        String requestId,
        Body error,
        VoiceAnalysisErrorResponse detail
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(String code, String message, Boolean retryable) {
    }

    /** 감싸인 형태로 왔으면 안쪽을 꺼낸다. */
    public String resolveCode() {
        if (error != null) {
            return error.code();
        }
        if (detail != null) {
            return detail.resolveCode();
        }
        return null;
    }
}
