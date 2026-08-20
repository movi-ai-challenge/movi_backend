package com.movi_backend.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanMoneyFormatterTest {

    @Test
    @DisplayName("금액을 변환하면 TTS가 읽기 쉬운 한국어 단위로 반환한다")
    void 금액을_변환하면_TTS가_읽기_쉬운_한국어_단위로_반환한다() {
        // given
        final long amount = 53_000L;

        // when
        final String result = KoreanMoneyFormatter.format(amount);

        // then
        assertThat(result).isEqualTo("5만 3천원");
    }

    @Test
    @DisplayName("0원을 변환하면 영원으로 반환한다")
    void 영원을_변환하면_영원으로_반환한다() {
        // given
        final long amount = 0L;

        // when
        final String result = KoreanMoneyFormatter.format(amount);

        // then
        assertThat(result).isEqualTo("영원");
    }

    @Test
    @DisplayName("여러 큰 단위가 있는 금액을 변환하면 단위 사이를 띄어 반환한다")
    void 여러_큰_단위가_있는_금액을_변환하면_단위_사이를_띄어_반환한다() {
        // given
        final long amount = 123_456_789L;

        // when
        final String result = KoreanMoneyFormatter.format(amount);

        // then
        assertThat(result).isEqualTo("1억 2천3백45만 6천7백89원");
    }
}
