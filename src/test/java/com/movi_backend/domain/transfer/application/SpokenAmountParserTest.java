package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpokenAmountParserTest {

    private final SpokenAmountParser parser = new SpokenAmountParser();

    // ========================================================================
    // 모델이 실제로 틀린 것들
    //
    // 운영에서 gpt-4o-mini 로 측정한 결과다. 이 파서를 두는 이유가 여기 있다.
    // ========================================================================

    @Test
    @DisplayName("십만 이천원 - 모델은 4회 중 4회 120,000으로 읽었다")
    void 십만_이천원() {
        assertThat(parser.parse("주혁에게 십만 이천원 보내줘")).contains(102_000L);
        assertThat(parser.parse("주혁에게 십만이천원 보내줘")).contains(102_000L);
    }

    @Test
    @DisplayName("자릿수가 겹쳐도 자리마다 더한다")
    void 복합_금액을_읽는다() {
        assertThat(parser.parse("엄마한테 십이만 삼천원 보내줘")).contains(123_000L);
        assertThat(parser.parse("주혁에게 만 오천원 보내줘")).contains(15_000L);
        assertThat(parser.parse("이십오만원 보내줘")).contains(250_000L);
        assertThat(parser.parse("백이십만원 보내줘")).contains(1_200_000L);
    }

    @Test
    @DisplayName("앞 숫자가 없는 자릿수는 1로 읽는다")
    void 앞_숫자가_없으면_하나로_읽는다() {
        assertThat(parser.parse("만원 보내줘")).contains(10_000L);
        assertThat(parser.parse("천원만 보내줘")).contains(1_000L);
        assertThat(parser.parse("백만원 보내줘")).contains(1_000_000L);
    }

    @Test
    @DisplayName("아라비아 숫자와 섞여도 같은 값으로 읽는다")
    void 숫자와_한글이_섞여도_읽는다() {
        assertThat(parser.parse("5만원 보내줘")).contains(50_000L);
        assertThat(parser.parse("50000원 보내줘")).contains(50_000L);
        assertThat(parser.parse("5,000원 보내줘")).contains(5_000L);
        assertThat(parser.parse("5만 2천원 보내줘")).contains(52_000L);
    }

    // ========================================================================
    // 금액이 아닌 숫자를 금액으로 읽지 않는다
    //
    // 발화에는 계좌번호가 함께 온다. 잘못 읽으면 엉뚱한 금액이 나간다.
    // ========================================================================

    @Test
    @DisplayName("계좌번호를 금액으로 읽지 않는다")
    void 계좌번호를_금액으로_읽지_않는다() {
        assertThat(parser.parse("삼오이이삼일오칠사구로 만원 보내줘")).contains(10_000L);
        assertThat(parser.parse("농협 352-2315749로 만 원 보내 줘")).contains(10_000L);
        assertThat(parser.parse("3522315749로 오만원 보내줘")).contains(50_000L);
    }

    @Test
    @DisplayName("계좌번호가 배수에 붙어 말이 안 되는 값이 되면 읽지 않는다")
    void 말이_안_되는_금액은_읽지_않는다() {
        // " 3522315749 만원" 을 그대로 풀면 조 단위가 된다. 한도에서 걸리기 전에
        // 확인 문구가 사용자에게 그 값을 읽어 준다.
        assertThat(parser.parse("계좌 3522315749 만원 보내줘")).isEmpty();
    }

    @Test
    @DisplayName("원으로 끝나는 낱말은 금액이 아니다")
    void 원으로_끝나는_낱말은_금액이_아니다() {
        assertThat(parser.parse("병원에 보내줘")).isEmpty();
        assertThat(parser.parse("지원 계좌로 보내줘")).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 금액이 둘이면 단정하지 않는다")
    void 금액이_둘이면_단정하지_않는다() {
        assertThat(parser.parse("만원 말고 오만원 보내줘")).isEmpty();
    }

    @Test
    @DisplayName("같은 금액을 두 번 말하면 그 값으로 읽는다")
    void 같은_금액을_두_번_말해도_읽는다() {
        assertThat(parser.parse("오만원, 오만원 보내줘")).contains(50_000L);
    }

    @Test
    @DisplayName("금액이 없으면 비운다 - 모델 값을 그대로 쓰게 둔다")
    void 금액이_없으면_비운다() {
        assertThat(parser.parse("주혁에게 보내줘")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }
}
