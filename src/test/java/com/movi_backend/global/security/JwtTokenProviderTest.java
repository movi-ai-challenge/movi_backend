package com.movi_backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String JWT_SECRET = "test-jwt-signing-key-that-is-long-enough-for-hs256";

    @Test
    @DisplayName("액세스 토큰을 검증하면 인증 사용자를 반환한다")
    void 액세스_토큰에서_인증_사용자를_반환한다() {
        // given
        final JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        final AuthUser authUser = AuthUser.of(42L, UserType.VISUALLY_IMPAIRED, 3L);
        final JwtTokenPair tokenPair = provider.issueTokenPair(authUser);

        // when
        final AuthUser parsed = provider.parseAccessToken(tokenPair.accessToken());

        // then
        assertThat(parsed).isEqualTo(authUser);
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰으로 사용하면 예외가 발생한다")
    void 리프레시_토큰을_액세스_토큰으로_사용하면_거부한다() {
        // given
        final JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        final JwtTokenPair tokenPair = provider.issueTokenPair(AuthUser.of(1L, UserType.GENERAL));

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken(tokenPair.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("만료된 액세스 토큰을 검증하면 만료 예외가 발생한다")
    void 만료된_액세스_토큰은_거부한다() {
        // given
        final JwtTokenProvider provider = provider(Duration.ofSeconds(-1));
        final JwtTokenPair tokenPair = provider.issueTokenPair(AuthUser.of(1L, UserType.GENERAL));

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken(tokenPair.accessToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("OAuth state 값이 변조되면 예외가 발생한다")
    void 변조된_OAuth_state는_거부한다() {
        // given
        final JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        final String state = provider.issueOauthState();
        final String tampered = state.substring(0, state.length() - 1) + "x";

        // when & then
        assertThatThrownBy(() -> provider.validateOauthState(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);
    }

    private JwtTokenProvider provider(final Duration accessExpiration) {
        final JwtProperties properties = new JwtProperties(
                "movi-test",
                JWT_SECRET,
                accessExpiration,
                Duration.ofDays(14),
                Duration.ofMinutes(5)
        );
        return new JwtTokenProvider(properties);
    }
}
