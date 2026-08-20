package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import java.time.LocalDateTime;

/**
 * 보호자 등록 요청 결과.
 *
 * <p><b>전화번호 원문·암호문과 초대 토큰은 담지 않는다.</b> 요청자는 보호자 본인이 아니므로
 * 초대 링크를 알 필요가 없고, 알면 보호자 확인 절차를 건너뛸 수 있다.
 */
public record GuardianLinkRequestResponse(
        Long linkId,
        GuardianLinkStatus status,
        String guardianName,
        String relation,
        LocalDateTime inviteExpiresAt,
        boolean invitationSent
) {

    public static GuardianLinkRequestResponse from(
            final GuardianLink guardianLink,
            final NotificationStatus notificationStatus
    ) {
        return new GuardianLinkRequestResponse(
                guardianLink.getId(),
                guardianLink.getStatus(),
                guardianLink.getGuardianName(),
                GuardianRelation.displayNameOrNull(guardianLink.getRelation()),
                guardianLink.getInviteExpiresAt(),
                notificationStatus == NotificationStatus.SENT
        );
    }

    /**
     * 사용자에게 읽어 줄 문구.
     *
     * <p>문자가 실패했는데 "보냈습니다"라고 읽으면 사용자는 보호자가 곧 승인할 것이라 믿고
     * 기다리게 된다. 실제로 일어난 일만 말한다.
     */
    public String toVoiceMessage() {
        if (invitationSent) {
            return "%s 님에게 보호자 연결 요청 문자를 보냈어요.".formatted(guardianName);
        }
        return "보호자 연결 요청은 저장했어요. 다만 문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요.";
    }
}
