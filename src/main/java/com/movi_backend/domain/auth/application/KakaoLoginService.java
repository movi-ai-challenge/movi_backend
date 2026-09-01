package com.movi_backend.domain.auth.application;

import com.movi_backend.domain.auth.dto.response.KakaoAuthorization;
import com.movi_backend.domain.auth.dto.response.KakaoTokenResponse;
import com.movi_backend.domain.auth.dto.response.KakaoUserInfo;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.OauthAccount;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.infrastructure.KakaoOAuthClient;
import com.movi_backend.domain.auth.repository.OauthAccountRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.OauthProvider;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.JwtTokenProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 로그인·최초 가입.
 *
 * <p>카카오는 전화번호를 회원 정보로 주지 않는다 — {@code users.phone}은 여기서 채우지 않고
 * PIN 등록 시점({@link AuthenticationService#registerPin})에 채운다.
 */
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String DEFAULT_USER_NAME = "Movi 사용자";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OauthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public KakaoAuthorization createAuthorization() {
        final String state = jwtTokenProvider.issueOauthState();
        final URI authorizationUri = kakaoOAuthClient.buildAuthorizationUri(state);
        return KakaoAuthorization.of(authorizationUri, state);
    }

    /**
     * 카카오 인가 코드로 사용자를 인증한다. <b>토큰은 발급하지 않는다.</b>
     *
     * <p>토큰을 여기서 만들면 리다이렉트로 넘길 때까지 어딘가에 들고 있어야 한다.
     * 교환 시점({@link #issueTokens})에 만들면 그 전까지는 존재하지 않는다.
     */
    @Transactional
    public LoginHandoffStore.Handoff authenticate(
            final String authorizationCode,
            final String state,
            final String stateCookie
    ) {
        final LoginUser loginUser = resolveUser(authorizationCode, state, stateCookie);
        return new LoginHandoffStore.Handoff(loginUser.user().getId(), loginUser.newUser());
    }

    private LoginUser resolveUser(
            final String authorizationCode,
            final String state,
            final String stateCookie
    ) {
        validateStateCookie(state, stateCookie);
        jwtTokenProvider.validateOauthState(state);
        final KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(authorizationCode);
        final KakaoUserInfo kakaoUser = kakaoOAuthClient.requestUserInfo(kakaoToken.accessToken());

        final LoginUser loginUser = findOrCreateUser(kakaoUser);
        validateActive(loginUser.user());
        return loginUser;
    }

    /** 인증된 사용자에게 토큰을 발급한다. 교환 코드를 소비한 직후에 호출한다. */
    @Transactional(readOnly = true)
    public LoginResponse issueTokens(final Long userId, final boolean newUser) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActive(user);
        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(user));
        return LoginResponse.of(user.getId(), user.getName(), newUser, tokenPair);
    }

    /**
     * 인증과 토큰 발급을 한 번에 한다.
     *
     * @deprecated 리다이렉트 URL 에 토큰을 실어 보내는 기존 방식 전용이다.
     *     프런트가 교환 코드로 옮겨오면 이 메서드와 호출부를 함께 지운다.
     */
    @Deprecated
    @Transactional
    public LoginResponse login(
            final String authorizationCode,
            final String state,
            final String stateCookie
    ) {
        final LoginUser loginUser = resolveUser(authorizationCode, state, stateCookie);
        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(loginUser.user()));
        return LoginResponse.of(
                loginUser.user().getId(),
                loginUser.user().getName(),
                loginUser.newUser(),
                tokenPair
        );
    }

    private LoginUser findOrCreateUser(final KakaoUserInfo kakaoUser) {
        final String providerUserId = kakaoUser.providerUserId();
        final OauthAccount existingAccount = oauthAccountRepository
                .findByProviderAndProviderUserId(OauthProvider.KAKAO, providerUserId)
                .orElse(null);
        if (existingAccount != null) {
            return LoginUser.existing(existingAccount.getUser());
        }

        final User user = createUser(kakaoUser.nickname());
        oauthAccountRepository.save(OauthAccount.builder()
                .user(user)
                .provider(OauthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
        return LoginUser.of(user, true);
    }

    private void validateStateCookie(final String state, final String stateCookie) {
        if (state == null || stateCookie == null) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
        }
        final boolean matches = MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8),
                stateCookie.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
        }
    }

    private User createUser(final String nickname) {
        final User user = User.builder()
                .name(resolveName(nickname))
                .userType(UserType.GENERAL)
                .build();
        return userRepository.save(user);
    }

    private String resolveName(final String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_USER_NAME;
        }
        return nickname.trim();
    }

    private void validateActive(final User user) {
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private AuthUser toAuthUser(final User user) {
        return AuthUser.of(user.getId(), user.getUserType(), user.getTokenVersion());
    }

    private record LoginUser(User user, boolean newUser) {

        private static LoginUser existing(final User user) {
            return new LoginUser(user, false);
        }

        private static LoginUser of(final User user, final boolean newUser) {
            return new LoginUser(user, newUser);
        }
    }
}
