package com.movi_backend.global.security;

import com.movi_backend.domain.auth.type.UserType;

/**
 * 인증된 사용자 정보. 컨트롤러에서 {@link CurrentUser}로 주입받는다.
 *
 * <p>인증 필터가 이 객체를 Spring Security {@code Authentication}의 principal로 넣으면
 * {@link CurrentUserArgumentResolver}가 꺼내서 전달한다.
 *
 * <p>엔티티가 아니라 식별자만 담는다. 컨트롤러가 User 엔티티를 통째로 들고 있을 이유가 없고,
 * 필요하면 서비스에서 조회한다.
 */
public record AuthUser(
        Long userId,
        UserType userType,
        long tokenVersion
) {
    public static AuthUser of(final Long userId, final UserType userType) {
        return new AuthUser(userId, userType, 0L);
    }

    public static AuthUser of(
            final Long userId,
            final UserType userType,
            final long tokenVersion
    ) {
        return new AuthUser(userId, userType, tokenVersion);
    }

    /** 화면을 보지 못하는 사용자인지 여부. 응답의 voiceMessage 채움 여부 판단에 쓴다. */
    public boolean needsVoiceGuidance() {
        return this.userType == UserType.VISUALLY_IMPAIRED || this.userType == UserType.SENIOR;
    }
}
