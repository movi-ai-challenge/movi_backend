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
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String DEFAULT_USER_NAME = "Movi 사용자";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OauthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SensitiveDataCrypto sensitiveDataCrypto;

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

        final String normalizedPhone = normalizePhone(kakaoUser.phoneNumber());
        final String phoneHash = sensitiveDataCrypto.hash(normalizedPhone);
        final Optional<User> existingUser = userRepository.findByPhoneHash(phoneHash);
        final User user;
        final boolean newUser;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            newUser = false;
        } else {
            user = createUser(kakaoUser.nickname(), normalizedPhone, phoneHash);
            newUser = true;
        }
        oauthAccountRepository.save(OauthAccount.builder()
                .user(user)
                .provider(OauthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
        return LoginUser.of(user, newUser);
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

    private User createUser(
            final String nickname,
            final String normalizedPhone,
            final String phoneHash
    ) {
        final User user = User.builder()
                .name(resolveName(nickname))
                .phone(sensitiveDataCrypto.encrypt(normalizedPhone))
                .phoneHash(phoneHash)
                .userType(UserType.GENERAL)
                .build();
        return userRepository.save(user);
    }

    private String normalizePhone(final String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_REQUIRED_INFO_MISSING);
        }
        final String compact = phoneNumber.replaceAll("[^0-9+]", "");
        final String normalized;
        if (compact.startsWith("+82")) {
            normalized = "0" + compact.substring(3);
        } else if (compact.startsWith("82")) {
            normalized = "0" + compact.substring(2);
        } else {
            normalized = compact;
        }
        if (!normalized.matches("^01[016789][0-9]{7,8}$")) {
            throw new BusinessException(ErrorCode.KAKAO_REQUIRED_INFO_MISSING);
        }
        return normalized;
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
