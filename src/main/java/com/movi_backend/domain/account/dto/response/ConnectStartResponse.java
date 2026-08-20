package com.movi_backend.domain.account.dto.response;

/**
 * 계좌 연결 시작 응답. 클라이언트는 이 URL로 사용자를 보낸다.
 */
public record ConnectStartResponse(String authorizationUrl) {

    public static ConnectStartResponse of(final String authorizationUrl) {
        return new ConnectStartResponse(authorizationUrl);
    }
}
