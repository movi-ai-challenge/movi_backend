package com.movi_backend.domain.transfer.dto.response;

import java.util.List;

/**
 * 등록 수취인 목록.
 *
 * <p>음성 대신 키보드로 송금할 때 고를 수 있는 사람이 여기 전부다. 화면을 보지 못하는
 * 사용자에게는 목록을 훑어 주는 대신 몇 명이 저장돼 있는지를 먼저 들려준다.
 */
public record RecipientListResponse(
        int totalCount,
        List<RecipientResponse> recipients
) {

    public static RecipientListResponse from(final List<RecipientResponse> recipients) {
        return new RecipientListResponse(recipients.size(), List.copyOf(recipients));
    }

    public String toVoiceMessage() {
        if (this.recipients.isEmpty()) {
            return "저장된 받는 분이 없어요.";
        }
        return "저장된 받는 분이 %d명 있어요.".formatted(this.totalCount);
    }
}
