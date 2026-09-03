package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpokenAccountNumberParserTest {

    private final SpokenAccountNumberParser parser = new SpokenAccountNumberParser();

    @Test
    @DisplayName("아라비아 숫자로 적힌 계좌번호를 읽는다")
    void 아라비아_숫자를_읽는다() {
        assertThat(parser.parse("농협 3522315749로 보내줘")).contains("3522315749");
    }

    @Test
    @DisplayName("한글로 적힌 자릿수를 읽는다")
    void 한글_자릿수를_읽는다() {
        assertThat(parser.parse("삼오이이삼일오칠사구")).contains("3522315749");
    }

    @Test
    @DisplayName("한글과 숫자가 섞여도 같은 자리로 읽는다")
    void 한글과_숫자가_섞여도_읽는다() {
        // 삼오이이 = 3522, 뒤는 그대로 315749
        assertThat(parser.parse("삼오이이 315749")).contains("3522315749");
    }

    @Test
    @DisplayName("자릿수를 품은 수사는 계좌번호로 읽지 않는다 - 없는 숫자가 생긴다")
    void 자릿수를_품은_수사는_읽지_않는다() {
        // "삼천오백이십..." 을 자리마다 읽으면 사용자가 말하지 않은 번호가 된다.
        assertThat(parser.parse("삼천오백이십만원 계좌로 보내줘")).isEmpty();
    }

    @Test
    @DisplayName("너무 짧으면 계좌번호로 보지 않는다")
    void 너무_짧으면_읽지_않는다() {
        assertThat(parser.parse("만원 보내줘")).isEmpty();
        assertThat(parser.parse("12345678")).isEmpty();
    }

    @Test
    @DisplayName("너무 길면 계좌번호로 보지 않는다")
    void 너무_길면_읽지_않는다() {
        assertThat(parser.parse("12345678901234567")).isEmpty();
    }

    @Test
    @DisplayName("빈 발화는 빈 값을 준다")
    void 빈_발화는_빈_값을_준다() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("  ")).isEmpty();
    }

    @Test
    @DisplayName("확인 문구는 자리마다 끊어 읽게 만든다 - TTS 가 억 단위로 읽으면 확인이 안 된다")
    void 확인_문구는_자리마다_끊어_읽는다() {
        assertThat(parser.toSpokenDigits("3522315749"))
                .isEqualTo("3 5 2 2 3 1 5 7 4 9");
    }
}
