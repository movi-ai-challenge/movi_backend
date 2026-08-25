package com.movi_backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanAmountFormatterTest {

    @Test
    @DisplayName("금액을 TTS가 읽을 수 있는 한국어로 바꾼다")
    void 금액을_한국어로_바꾼다() {
        // when & then
        assertThat(KoreanAmountFormatter.toKoreanWon(0L)).isEqualTo("영 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(1_000L)).isEqualTo("천 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(10_000L)).isEqualTo("만 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(15_000L)).isEqualTo("만 오천 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(50_000L)).isEqualTo("오만 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(53_000L)).isEqualTo("오만 삼천 원");
        assertThat(KoreanAmountFormatter.toKoreanWon(100_000_000L)).isEqualTo("일억 원");
    }

    @Test
    @DisplayName("만 단위가 1이면 '일만'이 아니라 '만'으로 읽는다")
    void 만_단위의_일은_생략한다() {
        // when & then
        assertThat(KoreanAmountFormatter.format(10_000L)).isEqualTo("만");
        assertThat(KoreanAmountFormatter.format(110_000L)).isEqualTo("십일만");
    }

    @Test
    @DisplayName("숫자를 그대로 이어 붙이지 않는다")
    void 숫자를_그대로_읽지_않는다() {
        // when
        final String spoken = KoreanAmountFormatter.toKoreanWon(50_000L);

        // then
        assertThat(spoken).doesNotContain("50000");
    }
}
