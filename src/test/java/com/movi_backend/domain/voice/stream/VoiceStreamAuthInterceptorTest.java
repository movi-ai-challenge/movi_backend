package com.movi_backend.domain.voice.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtProperties;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.JwtTokenProvider;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 음성 스트림 핸드셰이크 인증.
 *
 * <p>여기서 막지 못하면 인증 없이 오디오가 AI 로 흘러 STT 를 그대로 쓸 수 있다.
 */
class VoiceStreamAuthInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private VoiceStreamAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
                "movi-backend",
                "test-jwt-signing-key-must-be-at-least-32-bytes",
                null,
                null,
                null
        ));
        interceptor = new VoiceStreamAuthInterceptor(jwtTokenProvider);
    }

    private boolean handshake(final String query) {
        final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/ws/v1/voice/stream");
        if (query != null) {
            servletRequest.setQueryString(query);
        }
        final ServerHttpRequest request = new ServletServerHttpRequest(servletRequest) {
            @Override
            public URI getURI() {
                final String suffix = query == null ? "" : "?" + query;
                return URI.create("https://moviback.duckdns.org/ws/v1/voice/stream" + suffix);
            }
        };
        return interceptor.beforeHandshake(request, null, null, attributes);
    }

    private final Map<String, Object> attributes = new HashMap<>();

    @Test
    @DisplayName("토큰이 없으면 핸드셰이크를 거부한다")
    void 토큰이_없으면_거부한다() {
        assertThat(handshake(null)).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("빈 토큰도 거부한다")
    void 빈_토큰도_거부한다() {
        assertThat(handshake("accessToken=")).isFalse();
    }

    @Test
    @DisplayName("위조된 토큰은 거부한다")
    void 위조된_토큰은_거부한다() {
        assertThat(handshake("accessToken=forged.token.value")).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("유효한 토큰이면 통과시키고 사용자를 세션에 남긴다")
    void 유효한_토큰은_통과시킨다() {
        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(
                AuthUser.of(7L, UserType.GENERAL, 0L)
        );

        final boolean allowed = handshake("accessToken=" + tokenPair.accessToken());

        assertThat(allowed).isTrue();
        final AuthUser authUser =
                (AuthUser) attributes.get(VoiceStreamAuthInterceptor.AUTH_USER_ATTRIBUTE);
        assertThat(authUser.userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("리프레시 토큰으로는 연결할 수 없다")
    void 리프레시_토큰은_거부한다() {
        // 수명이 14일이라 스트림 접근에 쓰이면 노출 위험이 훨씬 커진다.
        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(
                AuthUser.of(7L, UserType.GENERAL, 0L)
        );

        assertThat(handshake("accessToken=" + tokenPair.refreshToken())).isFalse();
    }
}
