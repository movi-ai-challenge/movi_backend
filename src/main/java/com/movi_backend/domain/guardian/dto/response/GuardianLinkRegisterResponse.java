package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import com.movi_backend.domain.guardian.type.NotificationStatus;

/**
 * 보호자 등록 결과.
 *
 * <p>확인 절차 없이 바로 {@code ACTIVE}로 생성되므로, 이 응답이 오면 연결은 이미 완료된 것이다.
 * <b>전화번호 원문·암호문은 담지 않는다.</b>
 */
public record GuardianLinkRegisterResponse(
        Long linkId,
        GuardianLinkStatus status,
        String guardianName,
        String relation,
        boolean notificationSent
) {

    public static GuardianLinkRegisterResponse from(
            final GuardianLink guardianLink,
            final NotificationStatus notificationStatus
    ) {
        return new GuardianLinkRegisterResponse(
                guardianLink.getId(),
                guardianLink.getStatus(),
                guardianLink.getGuardianName(),
                GuardianRelation.displayNameOrNull(guardianLink.getRelation()),
                notificationStatus == NotificationStatus.SENT
        );
    }

    /**
     * 사용자에게 읽어 줄 문구.
     *
     * <p>문자가 실패했는데 "알렸어요"라고 읽으면 사용자는 보호자가 상황을 안다고 믿게 된다.
     * 연결 자체는 이미 끝났으므로, 실패해도 등록이 취소됐다고 오해하게 만들지 않는다.
     */
    public String toVoiceMessage() {
        if (notificationSent) {
            return "%s 님을 보호자로 등록했어요. 이상 거래가 감지되면 문자로 알려드릴게요.".formatted(guardianName);
        }
        return "%s 님을 보호자로 등록했어요. 다만 알림 문자는 보내지 못했어요.".formatted(guardianName);
    }
}
