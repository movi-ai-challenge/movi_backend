package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import java.time.LocalDateTime;

/** 보호자 연결 승인 결과. */
public record GuardianLinkApprovalResponse(
        Long linkId,
        Long protecteeUserId,
        GuardianLinkStatus status,
        String relation,
        LocalDateTime acceptedAt
) {

    public static GuardianLinkApprovalResponse from(final GuardianLink guardianLink) {
        return new GuardianLinkApprovalResponse(
                guardianLink.getId(),
                guardianLink.getProtecteeUser().getId(),
                guardianLink.getStatus(),
                GuardianRelation.displayNameOrNull(guardianLink.getRelation()),
                guardianLink.getAcceptedAt()
        );
    }

    public String toVoiceMessage() {
        return "보호자 연결이 완료됐어요.";
    }
}
