package com.movi_backend.domain.account.dto.response;

/**
 * 계좌 연결 완료 응답.
 *
 * @param registeredCount 이번에 새로 등록된 계좌 수
 * @param totalCount      연결된 전체 계좌 수
 */
public record ConnectResultResponse(int registeredCount, int totalCount) {

    public static ConnectResultResponse of(final int registeredCount, final int totalCount) {
        return new ConnectResultResponse(registeredCount, totalCount);
    }

    public String toVoiceMessage() {
        if (this.registeredCount == 0) {
            return "이미 연결된 계좌예요. 계좌가 %d개 있어요.".formatted(this.totalCount);
        }
        return "계좌 %d개를 연결했어요.".formatted(this.registeredCount);
    }
}
