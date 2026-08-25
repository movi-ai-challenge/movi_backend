package com.movi_backend.domain.guardian.type;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 피보호자와 보호자의 관계.
 *
 * <p>{@code guardian_links.relation}은 스키마상 VARCHAR지만 임의 문자열을 그대로 받으면
 * "자녀", "딸", "아들", "child"가 한 서비스 안에 섞인다. 저장값은 enum 이름으로 고정하고
 * 화면·음성에는 {@link #getDisplayName()}을 쓴다.
 *
 * <p>{@code OTHER}에 대한 자유입력 상세 컬럼은 현재 스키마에 없으므로 지원하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum GuardianRelation {

    CHILD("자녀"),
    SPOUSE("배우자"),
    SOCIAL_WORKER("사회복지사"),
    OTHER("기타");

    private final String displayName;

    /**
     * 요청값을 관계 enum으로 바꾼다. enum 이름과 한국어 표기를 모두 받는다.
     *
     * <p>프론트가 {@code CHILD}를 보내든 사용자가 음성으로 "자녀"라고 말하든 같은 값으로
     * 저장되어야 한다.
     *
     * @throws BusinessException 허용되지 않은 관계값일 때
     */
    public static GuardianRelation from(final String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_GUARDIAN_RELATION);
        }
        final String normalized = value.strip();
        for (final GuardianRelation relation : values()) {
            if (relation.matches(normalized)) {
                return relation;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_GUARDIAN_RELATION);
    }

    /**
     * 표시용 이름. 관계가 비어 있는 과거 데이터를 위해 {@code null}을 허용한다.
     *
     * <p>{@code relation} 컬럼은 nullable이라 초기 데이터에는 값이 없을 수 있다. 응답 조립 중에
     * NPE로 터지면 보호자 화면 전체가 열리지 않는다.
     */
    public static String displayNameOrNull(final GuardianRelation relation) {
        if (relation == null) {
            return null;
        }
        return relation.getDisplayName();
    }

    private boolean matches(final String normalized) {
        if (name().equalsIgnoreCase(normalized)) {
            return true;
        }
        return displayName.equals(normalized)
                || name().replace("_", "").equalsIgnoreCase(normalized.toUpperCase(Locale.KOREA));
    }
}
