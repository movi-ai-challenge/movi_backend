package com.movi_backend.domain.voice.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 음성 세션 시작 요청. 본문 전체가 선택이다.
 *
 * <p>{@code deviceUuid}를 보내면 그 기기가 세션에 연결돼 FDS의 신뢰 기기 피처로 쓰인다.
 * 보내지 않거나 등록되지 않은 기기면 세션은 그대로 만들어지고 이후 이체는 비신뢰 기기로
 * 평가된다 — 위험 쪽으로 기우는 것이라 안전한 실패다.
 */
public record VoiceSessionStartRequest(
        @Size(max = 100)
        String deviceUuid
) {
}
