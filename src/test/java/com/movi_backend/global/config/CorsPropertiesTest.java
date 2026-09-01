package com.movi_backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    @DisplayName("허용 오리진 설정이 비어 있으면 로컬·배포 프론트엔드 기본값을 사용한다")
    void 허용_오리진_설정이_비어_있으면_기본값을_사용한다() {
        // given
        final List<String> allowedOrigins = null;

        // when
        final CorsProperties properties = new CorsProperties(allowedOrigins);

        // then
        assertThat(properties.allowedOrigins())
                .contains("http://localhost:3000", "https://movi-frontend-amber.vercel.app");
    }

    @Test
    @DisplayName("허용 오리진을 설정하면 그 값을 그대로 쓴다")
    void 허용_오리진을_설정하면_그_값을_그대로_쓴다() {
        // given
        final List<String> allowedOrigins = List.of("https://example.com");

        // when
        final CorsProperties properties = new CorsProperties(allowedOrigins);

        // then
        assertThat(properties.allowedOrigins()).containsExactly("https://example.com");
    }
}
