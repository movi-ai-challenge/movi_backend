package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;

/** AI FDS 평가 서비스와 통신하는 경계. */
public interface FdsAssessmentClient {

    /** 이체 사실 데이터를 전달하고 검증된 위험도 평가를 반환한다. */
    FdsAssessmentResponse assess(FdsAssessmentRequest request);
}
