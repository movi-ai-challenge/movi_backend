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

    @Transactional
    public LoginResponse login(
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

        final AuthUser authUser = toAuthUser(loginUser.user());
        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(authUser);
        return LoginResponse.of(loginUser.user().getId(), loginUser.newUser(), tokenPair);
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
