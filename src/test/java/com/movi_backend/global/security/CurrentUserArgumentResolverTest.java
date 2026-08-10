package com.movi_backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentUserArgumentResolverTest {

    private static final String DEV_USER_ID_HEADER = "X-Dev-User-Id";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 정보가 있으면 SecurityContext의 사용자를 반환한다")
    void 인증된_사용자를_반환한다() {
        // given
        final AuthUser principal = AuthUser.of(42L, UserType.VISUALLY_IMPAIRED);
        authenticate(principal);
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(false, 1L));

        // when
        final Object resolved = resolver.resolveArgument(null, null, webRequest(null), null);

        // then
        assertThat(resolved).isEqualTo(principal);
    }

    @Test
    @DisplayName("인증 정보가 없고 dev-mode도 꺼져 있으면 예외가 발생한다")
    void 인증_정보가_없으면_거부한다() {
        // given
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(false, 1L));

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest(null), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("dev-mode에서 헤더로 사용자를 지정하면 해당 사용자를 반환한다")
    void dev_모드에서_헤더의_사용자를_반환한다() {
        // given
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(true, 1L));

        // when
        final Object resolved = resolver.resolveArgument(null, null, webRequest("99"), null);

        // then
        assertThat(resolved).isEqualTo(AuthUser.of(99L, UserType.GENERAL));
    }

    @Test
    @DisplayName("dev-mode에서 헤더가 없으면 설정된 기본 사용자를 반환한다")
    void dev_모드에서_기본_사용자를_반환한다() {
        // given
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(true, 7L));

        // when
        final Object resolved = resolver.resolveArgument(null, null, webRequest(null), null);

        // then
        assertThat(resolved).isEqualTo(AuthUser.of(7L, UserType.GENERAL));
    }

    @Test
    @DisplayName("dev-mode 헤더 값이 숫자가 아니면 예외가 발생한다")
    void dev_모드_헤더_형식이_잘못되면_거부한다() {
        // given
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(true, 1L));

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest("abc"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("인증 정보가 있으면 dev-mode 헤더보다 우선한다")
    void 인증_정보가_dev_모드보다_우선한다() {
        // given
        final AuthUser principal = AuthUser.of(42L, UserType.SENIOR);
        authenticate(principal);
        final CurrentUserArgumentResolver resolver =
                new CurrentUserArgumentResolver(new AuthProperties(true, 1L));

        // when
        final Object resolved = resolver.resolveArgument(null, null, webRequest("99"), null);

        // then
        assertThat(resolved).isEqualTo(principal);
    }

    private void authenticate(final AuthUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );
    }

    private ServletWebRequest webRequest(final String devUserIdHeader) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        if (devUserIdHeader != null) {
            request.addHeader(DEV_USER_ID_HEADER, devUserIdHeader);
        }
        return new ServletWebRequest(request);
    }
}
