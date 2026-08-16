package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.fds.client.dto.FdsProfileFeature;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FdsProfileFeatureTest {

    @Test
    @DisplayName("비초기 프로필의 집계 금액이 누락되면 예외가 발생한다")
    void 비초기_프로필의_집계_금액이_누락되면_예외가_발생한다() {
        // given
        final BigDecimal averageAmount = null;

        // when & then
        assertThatThrownBy(() -> FdsProfileFeature.of(
                averageAmount,
                new BigDecimal("100000"),
                new BigDecimal("11000"),
                8,
                3,
                List.of(9, 12, 18)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
