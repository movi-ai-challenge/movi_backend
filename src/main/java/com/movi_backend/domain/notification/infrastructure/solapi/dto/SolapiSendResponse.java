package com.movi_backend.domain.notification.infrastructure.solapi.dto;

/**
 * {@code POST /messages/v4/send} 응답.
 *
 * <p>필드명은 <a href="https://developers.solapi.com">솔라피 공식 문서</a> 기준으로 배포 전
 * 재확인한다. 알 수 없는 필드는 무시한다 — 문서와 실제 응답이 조금 달라도 파싱 자체가
 * 깨지지 않게 한다.
 */
public record SolapiSendResponse(
        String messageId,
        String groupId,
        String statusCode,
        String statusMessage
) {
}
