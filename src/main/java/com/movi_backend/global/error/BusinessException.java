package com.movi_backend.global.error;

import lombok.Getter;

/**
 * 비즈니스 예외.
 *
 * <p>{@code detailMessage}는 로그 추적용이며 사용자 응답에는 노출되지 않는다.
 * 계좌번호·전화번호·인증 토큰 등 민감정보를 넣지 않는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(final ErrorCode errorCode, final String detailMessage) {
        super("%s - %s".formatted(errorCode.getMessage(), detailMessage));
        this.errorCode = errorCode;
    }
}
