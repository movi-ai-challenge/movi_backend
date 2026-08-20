package com.movi_backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumberNormalizerTest {

    private static final String EXPECTED = "01012345678";

    @Test
    @DisplayName("표기 방식이 달라도 같은 번호로 정규화한다")
    void 전화번호를_정규화한다() {
        // when & then
        assertThat(PhoneNumberNormalizer.normalize("01012345678")).isEqualTo(EXPECTED);
        assertThat(PhoneNumberNormalizer.normalize("010-1234-5678")).isEqualTo(EXPECTED);
        assertThat(PhoneNumberNormalizer.normalize("010 1234 5678")).isEqualTo(EXPECTED);
        assertThat(PhoneNumberNormalizer.normalize("+82 10-1234-5678")).isEqualTo(EXPECTED);
        assertThat(PhoneNumberNormalizer.normalize("821012345678")).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("휴대전화 형식이 아니면 거부한다")
    void 잘못된_형식은_거부한다() {
        // when & then
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize("02-123-4567"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PHONE_NUMBER);
    }

    @Test
    @DisplayName("마스킹하면 가운데 자리가 가려진다")
    void 마스킹하면_가운데가_가려진다() {
        // when
        final String masked = PhoneNumberNormalizer.mask(EXPECTED);

        // then
        assertThat(masked).isEqualTo("010****5678");
        assertThat(masked).doesNotContain("1234");
    }
}
