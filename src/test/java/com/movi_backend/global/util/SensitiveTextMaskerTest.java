package com.movi_backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveTextMaskerTest {

    @Test
    @DisplayName("계좌번호가 포함된 문장을 마스킹하면 끝 네 자리만 남긴다")
    void 계좌번호가_포함된_문장을_마스킹하면_끝_네_자리만_남긴다() {
        // given
        final String text = "신한은행 110-123-123456 계좌로 보내줘";

        // when
        final String masked = SensitiveTextMasker.mask(text);

        // then
        assertThat(masked).isEqualTo("신한은행 ***3456 계좌로 보내줘");
        assertThat(masked).doesNotContain("110", "123-123456");
    }

    @Test
    @DisplayName("전화번호가 포함된 문장을 마스킹하면 구분자와 앞자리를 제거한다")
    void 전화번호가_포함된_문장을_마스킹하면_구분자와_앞자리를_제거한다() {
        // given
        final String text = "연락처는 010-1234-5678이야";

        // when
        final String masked = SensitiveTextMasker.mask(text);

        // then
        assertThat(masked).isEqualTo("연락처는 ***5678이야");
    }

    @Test
    @DisplayName("금액과 날짜만 있는 문장을 마스킹하면 원문을 유지한다")
    void 금액과_날짜만_있는_문장을_마스킹하면_원문을_유지한다() {
        // given
        final String text = "2026-08-16에 50000원 보내줘";

        // when
        final String masked = SensitiveTextMasker.mask(text);

        // then
        assertThat(masked).isEqualTo(text);
        assertThat(SensitiveTextMasker.containsSensitiveNumber(text)).isFalse();
    }
}
