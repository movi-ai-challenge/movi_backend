package com.movi_backend.global.error;

/**
 * 에러 응답.
 *
 * <p>{@code voiceMessage}는 클라이언트가 TTS로 읽어 줄 문구다.
 * 화면 표시용 {@code message}와 분리해 내려보낸다.
 */
public record ErrorResponse(
        String code,
        String message,
        String voiceMessage
) {
    public static ErrorResponse from(final ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getVoiceMessage()
        );
    }
}
