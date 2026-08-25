package com.movi_backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.auth.dto.response.KakaoTokenResponse;
import com.movi_backend.domain.auth.dto.response.KakaoUserInfo;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.infrastructure.KakaoOAuthClient;
import com.movi_backend.domain.auth.repository.OauthAccountRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private KakaoLoginService kakaoLoginService;

    @Test
    @DisplayName("OAuth state와 브라우저 쿠키가 다르면 로그인을 거부한다")
    void OAuth_state와_쿠키가_다르면_거부한다() {
        // given
        final String requestState = "request-state";
        final String cookieState = "different-cookie-state";

        // when & then
        assertThatThrownBy(() -> kakaoLoginService.login("authorization-code", requestState, cookieState))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OAUTH_STATE);
    }

    @Test
    @DisplayName("처음 카카오 로그인하면 전화번호 없이 사용자를 생성하고 JWT를 반환한다")
    void 처음_카카오_로그인하면_전화번호_없이_사용자와_JWT를_반환한다() {
        // given
        final String state = "matching-state";
        final KakaoTokenResponse kakaoToken =
                new KakaoTokenResponse("kakao-access", "bearer", null, 3600L);
        final KakaoUserInfo kakaoUser = new KakaoUserInfo(
                12345L,
                new KakaoUserInfo.KakaoAccount(new KakaoUserInfo.Profile("사용자"))
        );
        final User savedUser = User.builder()
                .name("사용자")
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(savedUser, "id", 7L);
        final JwtTokenPair tokenPair = JwtTokenPair.of("access-jwt", "refresh-jwt", 1800L);

        given(kakaoOAuthClient.requestToken("authorization-code")).willReturn(kakaoToken);
        given(kakaoOAuthClient.requestUserInfo("kakao-access")).willReturn(kakaoUser);
        given(oauthAccountRepository.findByProviderAndProviderUserId(any(), any()))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtTokenProvider.issueTokenPair(any())).willReturn(tokenPair);

        // when
        final LoginResponse response = kakaoLoginService.login(
                "authorization-code",
                state,
                state
        );

        // then
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.newUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(savedUser.getPhone()).isNull();
        assertThat(savedUser.getPhoneHash()).isNull();
    }
}
