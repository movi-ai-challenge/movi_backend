package com.movi_backend.global.response;

import com.movi_backend.global.error.ErrorCode;

/**
 * 모든 API의 공통 응답 형식.
 *
 * <p>성공·실패 응답의 구조가 동일하다. 클라이언트는 하나의 파서로 두 경우를 모두 처리하고,
 * {@code code}가 {@code SUCCESS}인지로 분기한다.
 *
 * <p>{@code voiceMessage}는 클라이언트가 TTS로 읽어 줄 문구다. 화면을 보지 못하는 사용자에게는
 * 이 필드가 유일한 피드백 수단이므로, 사용자에게 결과를 알려야 하는 응답에는 반드시 채운다.
 * 목록 조회처럼 화면 렌더링만 필요한 응답은 {@code null}로 둔다.
 *
 * <pre>
 * // 음성 안내가 필요한 경우
 * return ApiResponse.success(balance, "국민은행 통장에 5만 3천원 있어요.");
 *
 * // 화면 표시만 필요한 경우
 * return ApiResponse.success(transactions);
 * </pre>
 */
public record ApiResponse<T>(
        String code,
        String message,
        String voiceMessage,
        T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청이 정상 처리되었습니다.";

    /** 데이터만 반환한다. 음성 안내가 필요 없는 조회에 사용한다. */
    public static <T> ApiResponse<T> success(final T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, data);
    }

    /** 데이터와 함께 TTS로 읽을 문구를 반환한다. */
    public static <T> ApiResponse<T> success(final T data, final String voiceMessage) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, voiceMessage, data);
    }

    /** 반환할 데이터가 없는 경우에 사용한다. */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, null);
    }

    /** 반환할 데이터 없이 음성 안내만 필요한 경우에 사용한다. */
    public static ApiResponse<Void> successWithVoice(final String voiceMessage) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, voiceMessage, null);
    }

    /** 실패 응답. {@code GlobalExceptionHandler}에서만 사용한다. */
    public static ApiResponse<Void> error(final ErrorCode errorCode) {
        return new ApiResponse<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getVoiceMessage(),
                null
        );
    }
}
