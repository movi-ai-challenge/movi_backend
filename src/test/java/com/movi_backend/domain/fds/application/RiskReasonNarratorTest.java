package com.movi_backend.domain.fds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 위험 근거 안내 문구.
 *
 * <p>화면을 보지 않는 사용자는 이 문장만 듣는다. 영문 코드가 그대로 읽히면 안 된다.
 */
class RiskReasonNarratorTest {

    @Test
    @DisplayName("코드를 사람이 알아들을 말로 바꾼다")
    void 코드를_사람의_말로_바꾼다() {
        final List<String> described = RiskReasonNarrator.describe(
                List.of("NIGHT_TRANSACTION", "NEW_RECIPIENT")
        );

        assertThat(described).containsExactly("처음 보내는 계좌예요", "늦은 밤이나 새벽 시간이에요");
    }

    @Test
    @DisplayName("금액 관련 근거를 먼저 읽는다")
    void 금액_근거를_먼저_읽는다() {
        // 여러 근거가 잡히면 사용자가 가장 크게 느끼는 것부터 들려야 한다.
        final List<String> described = RiskReasonNarrator.describe(
                List.of("CROSS_BANK", "HIGH_AMOUNT_RATIO", "NIGHT_TRANSACTION")
        );

        assertThat(described.getFirst()).isEqualTo("평소보다 큰 금액이에요");
    }

    @Test
    @DisplayName("근거가 많아도 세 개까지만 읽는다")
    void 세_개까지만_읽는다() {
        // 다 읽으면 길어져 무엇이 중요한지 묻힌다.
        final List<String> described = RiskReasonNarrator.describe(List.of(
                "HIGH_AMOUNT_RATIO",
                "EXTREME_AMOUNT_ZSCORE",
                "NEW_RECIPIENT",
                "NIGHT_TRANSACTION",
                "CROSS_BANK"
        ));

        assertThat(described).hasSize(3);
    }

    @Test
    @DisplayName("모르는 코드는 읽지 않는다")
    void 모르는_코드는_읽지_않는다() {
        // AI 가 룰을 추가해도 영문 코드가 그대로 사용자에게 읽히면 안 된다.
        final List<String> described = RiskReasonNarrator.describe(
                List.of("SOME_NEW_RULE", "NEW_RECIPIENT")
        );

        assertThat(described).containsExactly("처음 보내는 계좌예요");
    }

    @Test
    @DisplayName("근거가 없으면 빈 문장이다")
    void 근거가_없으면_빈_문장이다() {
        assertThat(RiskReasonNarrator.toSentence(List.of())).isEmpty();
        assertThat(RiskReasonNarrator.toSentence(null)).isEmpty();
    }

    @Test
    @DisplayName("한 문장으로 이어 붙인다")
    void 한_문장으로_이어_붙인다() {
        final String sentence = RiskReasonNarrator.toSentence(
                List.of("NEW_RECIPIENT", "NIGHT_TRANSACTION")
        );

        assertThat(sentence).isEqualTo("처음 보내는 계좌예요, 늦은 밤이나 새벽 시간이에요.");
    }
}
