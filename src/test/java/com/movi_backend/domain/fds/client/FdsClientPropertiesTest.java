package com.movi_backend.domain.fds.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FdsClientPropertiesTest {

    @Test
    @DisplayName("FDS 설정이 비어 있으면 계약 타임아웃 기본값을 사용한다")
    void FDS_설정이_비어_있으면_계약_타임아웃_기본값을_사용한다() {
        // given
        final String baseUrl = null;

        // when
        final FdsClientProperties properties = new FdsClientProperties(baseUrl, null, null);

        // then
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.responseTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("FDS 응답 제한 시간이 0이면 설정 예외가 발생한다")
    void FDS_응답_제한_시간이_0이면_설정_예외가_발생한다() {
        // given
        final Duration responseTimeout = Duration.ZERO;

        // when & then
        assertThatThrownBy(() -> new FdsClientProperties(null, null, responseTimeout))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
