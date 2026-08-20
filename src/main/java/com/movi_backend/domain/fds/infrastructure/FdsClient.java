package com.movi_backend.domain.fds.infrastructure;

import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;

/**
 * FDS 예측 서비스 게이트웨이.
 *
 * <p>AI 파트의 추론 API가 열리기 전에도 이체 흐름 전체를 검증할 수 있도록 인터페이스를 먼저
 * 고정한다. {@code movi.fds.mode} 설정으로 {@link MockFdsClient}와 {@link HttpFdsClient}를 바꾼다.
 *
 * <p>구현체는 실패 시 {@code ErrorCode.ASSESSMENT_FAILED} 또는 {@code ASSESSMENT_TIMEOUT}으로
 * 예외를 던진다. <b>평가 실패는 곧 위험이며, 이체를 통과시키지 않는다.</b>
 */
public interface FdsClient {

    FraudPredictResponse predict(FraudPredictRequest request);
}
