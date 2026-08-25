package com.movi_backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.entity.UserCredential;
import com.movi_backend.domain.auth.repository.UserCredentialRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtTokenProvider;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("잠긴 상태에서 올바른 PIN을 입력해도 즉시 거부한다")
    void 잠긴_상태에서는_올바른_PIN도_거부한다() {
        // given
        final User user = user(1L);
        final UserCredential credential = credential(user);
        final LocalDateTime now = LocalDateTime.now();
        for (int attempt = 0; attempt < UserCredential.MAX_FAILED_ATTEMPTS; attempt++) {
            credential.recordFailure(now);
        }
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L)).willReturn(Optional.of(credential));

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPin(
                new PinLoginRequest("010-1234-5678", "123456")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PIN_LOCKED);
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 PIN을 입력하면 실패 횟수를 증가시킨다")
    void 잘못된_PIN은_실패_횟수를_증가시킨다() {
        // given
        final User user = user(1L);
        final UserCredential credential = credential(user);
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L)).willReturn(Optional.of(credential));
        given(passwordEncoder.matches("000000", "encoded-pin")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPin(
                new PinLoginRequest("01012345678", "000000")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PIN_MISMATCH);
        assertThat(credential.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("올바른 PIN으로 로그인하면 실패 횟수를 초기화하고 JWT를 반환한다")
    void 올바른_PIN으로_로그인하면_JWT를_반환한다() {
        // given
        final User user = user(1L);
        final UserCredential credential = credential(user);
        credential.recordFailure(LocalDateTime.now());
        final JwtTokenPair tokenPair = JwtTokenPair.of("access-token", "refresh-token", 1800L);
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L)).willReturn(Optional.of(credential));
        given(passwordEncoder.matches("123456", "encoded-pin")).willReturn(true);
        given(jwtTokenProvider.issueTokenPair(AuthUser.of(1L, UserType.GENERAL, 0L)))
                .willReturn(tokenPair);

        // when
        final LoginResponse response = authenticationService.loginWithPin(
                new PinLoginRequest("01012345678", "123456")
        );

        // then
        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("로그아웃하면 사용자의 토큰 버전을 증가시킨다")
    void 로그아웃하면_토큰_버전을_증가시킨다() {
        // given
        final User user = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        authenticationService.logout(1L);

        // then
        assertThat(user.getTokenVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그아웃 이전에 발급된 리프레시 토큰으로 갱신하면 예외가 발생한다")
    void 로그아웃_이전에_발급된_리프레시_토큰은_갱신을_거부한다() {
        // given
        final User user = user(1L);
        user.invalidateTokens();
        given(jwtTokenProvider.parseRefreshToken("old-refresh-token"))
                .willReturn(AuthUser.of(1L, UserType.GENERAL, 0L));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authenticationService.refresh("old-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("PIN을 등록하면 전화번호를 채우고 자격증명을 생성한다")
    void PIN을_등록하면_전화번호를_채우고_자격증명을_생성한다() {
        // given
        final User user = userWithoutPhone(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(sensitiveDataCrypto.encrypt("01012345678")).willReturn("encrypted-phone");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.empty());
        given(passwordEncoder.encode("123456")).willReturn("encoded-pin");

        // when
        authenticationService.registerPin(1L, "010-1234-5678", "123456");

        // then
        assertThat(user.getPhone()).isEqualTo("encrypted-phone");
        assertThat(user.getPhoneHash()).isEqualTo("phone-hash");
        then(userCredentialRepository).should().save(any(UserCredential.class));
    }

    @Test
    @DisplayName("이미 다른 계정이 쓰는 전화번호로 PIN을 등록하면 거부한다")
    void 이미_다른_계정이_쓰는_전화번호로_PIN을_등록하면_거부한다() {
        // given
        final User user = userWithoutPhone(1L);
        final User otherUser = userWithoutPhone(2L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(otherUser));

        // when & then
        assertThatThrownBy(() -> authenticationService.registerPin(1L, "01012345678", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PHONE_ALREADY_REGISTERED);
        then(userCredentialRepository).should(never()).save(any(UserCredential.class));
    }

    @Test
    @DisplayName("이미 PIN이 등록되어 있으면 다시 등록할 수 없다")
    void 이미_PIN이_등록되어_있으면_다시_등록할_수_없다() {
        // given
        final User user = userWithoutPhone(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userCredentialRepository.existsByUserId(1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authenticationService.registerPin(1L, "01012345678", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PIN_ALREADY_REGISTERED);
        then(userRepository).should(never()).findByPhoneHash(any());
    }

    private User userWithoutPhone(final Long userId) {
        final User user = User.builder()
                .name("사용자")
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private User user(final Long userId) {
        final User user = User.builder()
                .name("사용자")
                .phone("encrypted-phone")
                .phoneHash("phone-hash")
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private UserCredential credential(final User user) {
        return UserCredential.builder()
                .user(user)
                .pinHash("encoded-pin")
                .biometricEnabled(false)
                .build();
    }
}
