package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;

/**
 * 보호자 등록 결과.
 *
 * <p>확인 절차 없이 바로 {@code ACTIVE}로 성립하므로, 이 응답이 오면 연결은 이미 끝난 것이다.
 * <b>전화번호 원문·암호문은 담지 않는다.</b>
 */
public record GuardianLinkRegisterResponse(
        Long linkId,
        GuardianLinkStatus status,
        String guardianName,
        String relation
) {

    public static GuardianLinkRegisterResponse from(final GuardianLink guardianLink) {
        return new GuardianLinkRegisterResponse(
                guardianLink.getId(),
                guardianLink.getStatus(),
                guardianLink.getGuardianName(),
                guardianLink.getRelation()
        );
    }

    public String toVoiceMessage() {
        return "%s 님을 보호자로 등록했어요. 이상 거래가 감지되면 문자로 알려드릴게요."
                .formatted(guardianName);
    }
}
