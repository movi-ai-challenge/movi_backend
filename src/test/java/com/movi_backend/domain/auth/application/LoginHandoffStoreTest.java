package com.movi_backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoginHandoffStoreTest {

    @Test
    @DisplayName("발급한 코드를 교환하면 로그인 대상을 돌려준다")
    void 발급한_코드를_교환하면_로그인_대상을_돌려준다() {
        // given
        final LoginHandoffStore store = new LoginHandoffStore();
        final String code = store.issue(7L, true);

        // when
        final LoginHandoffStore.Handoff handoff = store.consume(code);

        // then
        assertThat(handoff).isNotNull();
        assertThat(handoff.userId()).isEqualTo(7L);
        assertThat(handoff.newUser()).isTrue();
    }

    @Test
    @DisplayName("같은 코드를 두 번 교환하면 두 번째는 거부한다")
    void 같은_코드는_한_번만_쓰인다() {
        // given — 코드가 새어 나가도 이미 교환됐으면 쓸 수 없어야 한다.
        final LoginHandoffStore store = new LoginHandoffStore();
        final String code = store.issue(7L, false);

        // when
        final LoginHandoffStore.Handoff first = store.consume(code);
        final LoginHandoffStore.Handoff second = store.consume(code);

        // then
        assertThat(first).isNotNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("만료된 코드는 교환하지 못한다")
    void 만료된_코드는_거부한다() {
        // given
        final LoginHandoffStore store = new LoginHandoffStore();
        final String code = store.issue(7L, false);
        expire(store, code);

        // when & then
        assertThat(store.consume(code)).isNull();
    }

    @Test
    @DisplayName("발급하지 않은 코드는 교환하지 못한다")
    void 알_수_없는_코드는_거부한다() {
        // given
        final LoginHandoffStore store = new LoginHandoffStore();

        // when & then
        assertThat(store.consume("never-issued")).isNull();
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume(" ")).isNull();
    }

    @Test
    @DisplayName("발급할 때마다 다른 코드가 나온다")
    void 코드는_매번_다르다() {
        // given
        final LoginHandoffStore store = new LoginHandoffStore();

        // when
        final Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            codes.add(store.issue((long) i, false));
        }

        // then
        assertThat(codes).hasSize(200);
    }

    @Test
    @DisplayName("코드는 URL 에 그대로 실을 수 있는 문자만 쓴다")
    void 코드는_URL_안전_문자만_쓴다() {
        // given — 리다이렉트 쿼리에 들어가므로 인코딩 없이 안전해야 한다.
        final LoginHandoffStore store = new LoginHandoffStore();

        // when
        final String code = store.issue(1L, false);

        // then
        assertThat(code).matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * 만료를 흉내낸다.
     *
     * <p>Entry 가 record 라 필드를 바꿀 수 없어, 만료 시각이 지난 새 Entry 로 갈아 끼운다.
     * 실제 시간을 기다리면 테스트가 60초 걸린다.
     */
    @SuppressWarnings("unchecked")
    private void expire(final LoginHandoffStore store, final String code) {
        try {
            final Map<String, Object> handoffs =
                    (Map<String, Object>) ReflectionTestUtils.getField(store, "handoffs");
            final Class<?> entryType = Class.forName(
                    "com.movi_backend.domain.auth.application.LoginHandoffStore$Entry");
            final Constructor<?> constructor =
                    entryType.getDeclaredConstructor(Long.class, boolean.class, LocalDateTime.class);
            constructor.setAccessible(true);
            handoffs.put(code, constructor.newInstance(1L, false, LocalDateTime.now().minusSeconds(1)));
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("만료 상태를 만들지 못했습니다.", exception);
        }
    }
}
