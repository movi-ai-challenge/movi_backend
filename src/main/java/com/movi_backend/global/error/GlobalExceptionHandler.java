package com.movi_backend.global.error;

import com.movi_backend.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 처리.
 *
 * <p>성공 응답과 동일한 {@link ApiResponse} 구조로 내려보내, 클라이언트가 하나의 파서로
 * 두 경우를 모두 처리할 수 있게 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(final BusinessException exception) {
        final ErrorCode errorCode = exception.getErrorCode();
        log.warn("[{}] {}", errorCode.getCode(), exception.getMessage());
        return toResponse(errorCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(final Exception exception) {
        log.error("처리되지 않은 예외", exception);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        final java.util.List<String> invalidFields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField())
                .distinct()
                .toList();
        log.warn("요청 값 검증 실패: fields={}", invalidFields);
        return toObjectResponse(ErrorCode.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            final HttpRequestMethodNotSupportedException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        return toObjectResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            final HttpMediaTypeNotSupportedException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        return toObjectResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Spring이 직접 처리하는 예외(존재하지 않는 경로, 파라미터 누락 등)의 응답 본문을
     * {@link ApiResponse}로 교체한다.
     *
     * <p>이 훅이 없으면 404 같은 응답만 Spring 기본 ProblemDetail 형식으로 나가,
     * "모든 응답은 같은 구조"라는 규약이 깨진다. 클라이언트가 {@code code}로 분기하므로
     * 형식이 다른 응답이 하나라도 있으면 예외 처리를 두 벌 만들어야 한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception exception,
            final Object body,
            final HttpHeaders headers,
            final HttpStatusCode statusCode,
            final WebRequest request
    ) {
        if (body instanceof ApiResponse) {
            return new ResponseEntity<>(body, headers, statusCode);
        }
        final ErrorCode errorCode = resolveErrorCode(statusCode);
        log.warn("[{}] {}", errorCode.getCode(), exception.getMessage());
        return new ResponseEntity<>(ApiResponse.error(errorCode), headers, statusCode);
    }

    private ErrorCode resolveErrorCode(final HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCode.NOT_FOUND;
        }
        if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (statusCode.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }
        return ErrorCode.BAD_REQUEST;
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(final ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    private ResponseEntity<Object> toObjectResponse(final ErrorCode errorCode) {
        final HttpStatus httpStatus = errorCode.getHttpStatus();
        return new ResponseEntity<>(ApiResponse.error(errorCode), httpStatus);
    }
}
