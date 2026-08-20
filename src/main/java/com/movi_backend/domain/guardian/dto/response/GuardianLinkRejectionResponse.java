package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;

/** 보호자 연결 거절 결과. */
public record GuardianLinkRejectionResponse(
        Long linkId,
        GuardianLinkStatus status
) {

    public static GuardianLinkRejectionResponse from(final GuardianLink guardianLink) {
        return new GuardianLinkRejectionResponse(guardianLink.getId(), guardianLink.getStatus());
    }

    public String toVoiceMessage() {
        return "보호자 연결 요청을 거절했어요.";
    }
}
