package com.movi_backend.global.security;

import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser}가 붙은 파라미터에 {@link AuthUser}를 주입한다.
 *
 * <p>정상 경로는 인증 필터가 SecurityContext에 넣어 둔 principal을 꺼내는 것이다.
 * 인증 필터를 구현할 때 principal 타입을 {@link AuthUser}로 맞추면 이 리졸버가 그대로 동작한다.
 *
 * <p>{@code movi.auth.dev-mode=true}이면 인증 없이 {@code X-Dev-User-Id} 헤더로 사용자를
 * 지정할 수 있다. JWT 구현 전 다른 파트가 API를 개발·테스트하기 위한 장치이며,
 * <b>운영 환경에서는 반드시 꺼야 한다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String DEV_USER_ID_HEADER = "X-Dev-User-Id";

    private final AuthProperties authProperties;

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            final MethodParameter parameter,
            final ModelAndViewContainer mavContainer,
            final NativeWebRequest webRequest,
            final WebDataBinderFactory binderFactory
    ) {
        final AuthUser authenticated = findAuthenticatedUser();
        if (authenticated != null) {
            return authenticated;
        }
        if (authProperties.devMode()) {
            return resolveDevUser(webRequest);
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private AuthUser findAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        final Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser authUser) {
            return authUser;
        }
        return null;
    }

    private AuthUser resolveDevUser(final NativeWebRequest webRequest) {
        final Long userId = readDevUserId(webRequest);
        log.warn("dev-mode 인증 사용 중 (userId={}). 운영 환경에서는 movi.auth.dev-mode를 꺼야 한다.", userId);
        return AuthUser.of(userId, UserType.GENERAL);
    }

    private Long readDevUserId(final NativeWebRequest webRequest) {
        final HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return authProperties.devUserId();
        }
        final String header = request.getHeader(DEV_USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            return authProperties.devUserId();
        }
        try {
            return Long.valueOf(header.trim());
        } catch (final NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, DEV_USER_ID_HEADER + " 형식 오류");
        }
    }
}
