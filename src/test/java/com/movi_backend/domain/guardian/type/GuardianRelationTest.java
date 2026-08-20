package com.movi_backend.domain.guardian.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardianRelationTest {

    @Test
    @DisplayName("enum 이름과 한국어 표기를 모두 같은 값으로 해석한다")
    void 관계값을_해석한다() {
        // when & then
        assertThat(GuardianRelation.from("CHILD")).isEqualTo(GuardianRelation.CHILD);
        assertThat(GuardianRelation.from("child")).isEqualTo(GuardianRelation.CHILD);
        assertThat(GuardianRelation.from("자녀")).isEqualTo(GuardianRelation.CHILD);
        assertThat(GuardianRelation.from("사회복지사")).isEqualTo(GuardianRelation.SOCIAL_WORKER);
    }

    @Test
    @DisplayName("허용되지 않은 관계값은 거부한다")
    void 허용되지_않은_관계값은_거부한다() {
        // when & then
        assertThatThrownBy(() -> GuardianRelation.from("옆집아저씨"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_GUARDIAN_RELATION);
    }

    @Test
    @DisplayName("빈 관계값은 거부한다")
    void 빈_관계값은_거부한다() {
        // when & then
        assertThatThrownBy(() -> GuardianRelation.from("  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("관계가 비어 있어도 표시용 변환은 터지지 않는다")
    void 관계가_없으면_null을_반환한다() {
        // when & then
        assertThat(GuardianRelation.displayNameOrNull(null)).isNull();
    }
}
