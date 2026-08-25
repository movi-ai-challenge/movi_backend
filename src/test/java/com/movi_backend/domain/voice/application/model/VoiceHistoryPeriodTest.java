package com.movi_backend.domain.voice.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoiceHistoryPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    @Test
    @DisplayName("AI가 기간을 주지 않으면 최근 7일로 채운다")
    void AI가_기간을_주지_않으면_최근_7일로_채운다() {
        // when
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(null, null, TODAY);

        // then
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(period.endDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("시작일만 있으면 종료일을 오늘로 채운다")
    void 시작일만_있으면_종료일을_오늘로_채운다() {
        // when
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(
                LocalDate.of(2026, 8, 1),
                null,
                TODAY
        );

        // then
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(period.endDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("종료일만 있으면 시작일을 7일 전으로 채운다")
    void 종료일만_있으면_시작일을_7일_전으로_채운다() {
        // when
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(
                null,
                LocalDate.of(2026, 8, 20),
                TODAY
        );

        // then
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    @DisplayName("시작일이 종료일보다 뒤면 조회를 거부한다")
    void 시작일이_종료일보다_뒤면_조회를_거부한다() {
        // expect
        assertThatThrownBy(() -> VoiceHistoryPeriod.resolve(
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 10),
                TODAY
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HISTORY_PERIOD_INVALID);
    }

    @Test
    @DisplayName("미래 종료일을 요청하면 조회를 거부한다")
    void 미래_종료일을_요청하면_조회를_거부한다() {
        // expect
        assertThatThrownBy(() -> VoiceHistoryPeriod.resolve(
                TODAY,
                TODAY.plusDays(1),
                TODAY
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HISTORY_PERIOD_INVALID);
    }

    @Test
    @DisplayName("미래 시작일을 요청하면 조회를 거부한다")
    void 미래_시작일을_요청하면_조회를_거부한다() {
        // expect
        assertThatThrownBy(() -> VoiceHistoryPeriod.resolve(
                TODAY.plusDays(1),
                null,
                TODAY
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HISTORY_PERIOD_INVALID);
    }

    @Test
    @DisplayName("오늘이 종료일이면 오늘까지로 짧게 읽는다")
    void 오늘이_종료일이면_오늘까지로_짧게_읽는다() {
        // when
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(
                LocalDate.of(2026, 8, 1),
                null,
                TODAY
        );

        // then
        assertThat(period.toVoicePhrase(TODAY)).isEqualTo("8월 1일부터 오늘까지");
    }

    @Test
    @DisplayName("지난 기간은 시작일과 종료일을 모두 읽는다")
    void 지난_기간은_시작일과_종료일을_모두_읽는다() {
        // when
        final VoiceHistoryPeriod period = VoiceHistoryPeriod.resolve(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                TODAY
        );

        // then
        assertThat(period.toVoicePhrase(TODAY)).isEqualTo("7월 1일부터 7월 31일까지");
    }
}
