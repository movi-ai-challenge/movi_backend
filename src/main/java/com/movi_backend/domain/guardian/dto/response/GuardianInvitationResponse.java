package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import java.time.LocalDateTime;

/**
 * 초대 링크로 확인하는 연결 요청 내용.
 *
 * <p>이 응답은 <b>로그인 전에도 조회된다.</b> 토큰만 알면 볼 수 있으므로 피보호자의 이름 외에
 * 개인정보를 담지 않는다. 전화번호·계좌·오픈뱅킹 정보는 포함하지 않는다.
 */
public record GuardianInvitationResponse(
        Long linkId,
        String protecteeName,
        String guardianName,
        String relation,
        GuardianLinkStatus status,
        LocalDateTime expiresAt
) {

    public static GuardianInvitationResponse from(final GuardianLink guardianLink) {
        return new GuardianInvitationResponse(
                guardianLink.getId(),
                guardianLink.getProtecteeUser().getName(),
                guardianLink.getGuardianName(),
                GuardianRelation.displayNameOrNull(guardianLink.getRelation()),
                guardianLink.getStatus(),
                guardianLink.getInviteExpiresAt()
        );
    }

    public String toVoiceMessage() {
        return "%s 님이 보호자 연결을 요청했어요.".formatted(protecteeName);
    }
}
