package com.movi_backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.auth.application.event.TrustedDeviceRegistrationRequested;
import com.movi_backend.domain.auth.dto.request.PasswordLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinRegisterRequest;
import com.movi_backend.domain.auth.dto.request.SignUpRequest;
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
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                new PinLoginRequest("010-1234-5678", "123456", null, null, null)
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
                new PinLoginRequest("01012345678", "000000", null, null, null)
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
                new PinLoginRequest("01012345678", "123456", null, null, null)
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

    private User userWithLoginId(final Long userId, final String loginId) {
        final User user = User.builder()
                .name("사용자")
                .loginId(loginId)
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private UserCredential passwordCredential(final User user) {
        return UserCredential.builder()
                .user(user)
                .passwordHash("encoded-password")
                .biometricEnabled(false)
                .build();
    }

    @Test
    @DisplayName("PIN 로그인에 성공하면 그 기기를 신뢰 기기로 등록한다")
    void PIN_로그인에_성공하면_그_기기를_신뢰_기기로_등록한다() {
        // given
        final User user = user(1L);
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(credential(user)));
        given(passwordEncoder.matches("123456", "encoded-pin")).willReturn(true);
        given(jwtTokenProvider.issueTokenPair(AuthUser.of(1L, UserType.GENERAL, 0L)))
                .willReturn(JwtTokenPair.of("access-token", "refresh-token", 1800L));

        // when
        authenticationService.loginWithPin(new PinLoginRequest(
                "01012345678", "123456", "device-uuid-1", "Galaxy S24", "Android 14"
        ));

        // then
        then(eventPublisher).should().publishEvent(new TrustedDeviceRegistrationRequested(
                user.getId(), "device-uuid-1", "Galaxy S24", "Android 14"
        ));
    }

    @Test
    @DisplayName("PIN이 틀리면 기기를 신뢰 기기로 등록하지 않는다")
    void PIN이_틀리면_기기를_신뢰_기기로_등록하지_않는다() {
        // given — 인증에 실패한 기기를 신뢰하면 신뢰 기기 피처 자체가 의미를 잃는다
        final User user = user(1L);
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(credential(user)));
        given(passwordEncoder.matches("000000", "encoded-pin")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPin(new PinLoginRequest(
                "01012345678", "000000", "device-uuid-1", null, null
        ))).isInstanceOf(BusinessException.class);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일반 회원가입에 성공하면 곧바로 JWT를 반환한다")
    void 일반_회원가입에_성공하면_곧바로_JWT를_반환한다() {
        // given — 화면을 못 보는 사용자에게 가입 직후 로그인을 또 시키지 않는다
        final JwtTokenPair tokenPair = JwtTokenPair.of("access-token", "refresh-token", 1800L);
        given(userRepository.existsByLoginId("movi")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> {
                    final User saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 1L);
                    return saved;
                });
        given(jwtTokenProvider.issueTokenPair(AuthUser.of(1L, UserType.GENERAL, 0L)))
                .willReturn(tokenPair);

        // when
        final LoginResponse response = authenticationService.signUp(new SignUpRequest(
                "movi", "password123", "사용자", null, null, null, null
        ));

        // then
        assertThat(response.newUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("대문자로 가입한 아이디를 소문자로 정규화해 중복을 막는다")
    void 아이디는_대소문자를_구분하지_않는다() {
        // given — Movi 로 가입한 사람이 movi 로 로그인해도 같은 계정이어야 한다
        given(userRepository.existsByLoginId("movi")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authenticationService.signUp(new SignUpRequest(
                "MoVi", "password123", "사용자", null, null, null, null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_ID_ALREADY_REGISTERED);
        then(userRepository).should(org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 불일치와 같은 오류를 준다")
    void 없는_아이디도_비밀번호_불일치와_같은_오류를_준다() {
        // given — 응답이 갈리면 어떤 아이디가 가입돼 있는지 밖에서 확인할 수 있다
        given(userRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPassword(
                new PasswordLoginRequest("nobody", "password123", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("카카오로만 가입해 비밀번호가 없는 계정은 일반 로그인을 거부한다")
    void 비밀번호가_없는_계정은_일반_로그인을_거부한다() {
        // given
        final User user = userWithLoginId(1L, "movi");
        given(userRepository.findByLoginId("movi")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(credential(user)));

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPassword(
                new PasswordLoginRequest("movi", "password123", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_NOT_REGISTERED);
    }

    @Test
    @DisplayName("비밀번호를 연속으로 틀리면 PIN 로그인까지 함께 잠긴다")
    void 비밀번호_실패는_PIN_로그인까지_잠근다() {
        // given — 계정 단위 잠금이라 수단을 바꿔 시도 횟수를 늘릴 수 없다
        final User user = userWithLoginId(1L, "movi");
        final UserCredential credential = UserCredential.builder()
                .user(user)
                .pinHash("encoded-pin")
                .passwordHash("encoded-password")
                .biometricEnabled(false)
                .build();
        for (int attempt = 0; attempt < UserCredential.MAX_FAILED_ATTEMPTS; attempt++) {
            credential.recordFailure(LocalDateTime.now());
        }
        given(userRepository.findByLoginId("movi")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L)).willReturn(Optional.of(credential));

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPassword(
                new PasswordLoginRequest("movi", "password123", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_LOCKED);
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 기기를 신뢰 기기로 등록하지 않는다")
    void 비밀번호가_틀리면_기기를_신뢰하지_않는다() {
        // given
        final User user = userWithLoginId(1L, "movi");
        given(userRepository.findByLoginId("movi")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(passwordCredential(user)));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPassword(new PasswordLoginRequest(
                "movi", "wrong-password", "device-uuid-1", null, null
        ))).isInstanceOf(BusinessException.class);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일반 가입자도 PIN을 등록할 수 있다")
    void 일반_가입자도_PIN을_등록할_수_있다() {
        // given — 비밀번호만 담긴 자격증명 행이 이미 있다. 행의 존재만으로 거절하면
        //         일반 가입자는 PIN 을 영영 등록할 수 없다.
        final User user = userWithLoginId(1L, "movi");
        final UserCredential credential = passwordCredential(user);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(sensitiveDataCrypto.encrypt("01012345678")).willReturn("encrypted-phone");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.empty());
        given(userCredentialRepository.findByUserId(1L)).willReturn(Optional.of(credential));
        given(passwordEncoder.encode("135790")).willReturn("encoded-pin");

        // when
        authenticationService.registerPin(
                1L, new PinRegisterRequest("010-1234-5678", "135790", null, null, null)
        );

        // then — 새 행을 만들지 않고 기존 행에 PIN 을 채운다. 비밀번호도 그대로 남는다.
        assertThat(credential.getPinHash()).isEqualTo("encoded-pin");
        assertThat(credential.getPasswordHash()).isEqualTo("encoded-password");
        then(userCredentialRepository).should(org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(UserCredential.class));
    }

    @Test
    @DisplayName("PIN이 이미 있으면 다시 등록할 수 없다")
    void PIN이_이미_있으면_다시_등록할_수_없다() {
        // given
        final User user = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(sensitiveDataCrypto.encrypt("01012345678")).willReturn("encrypted-phone");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.empty());
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(credential(user)));

        // when & then
        assertThatThrownBy(() -> authenticationService.registerPin(
                1L, new PinRegisterRequest("010-1234-5678", "135790", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PIN_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("PIN이 없는 계정의 PIN 로그인은 미등록으로 거절한다")
    void PIN이_없으면_미등록으로_거절한다() {
        // given — 비밀번호만 있는 계정. BCrypt 가 null 을 false 로 넘겨 "불일치"로
        //         보이게 두지 않고, 무엇이 문제인지 분명한 코드를 준다.
        final User user = userWithLoginId(1L, "movi");
        given(sensitiveDataCrypto.hash("01012345678")).willReturn("phone-hash");
        given(userRepository.findByPhoneHash("phone-hash")).willReturn(Optional.of(user));
        given(userCredentialRepository.findByUserId(1L))
                .willReturn(Optional.of(passwordCredential(user)));

        // when & then
        assertThatThrownBy(() -> authenticationService.loginWithPin(
                new PinLoginRequest("01012345678", "135790", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PIN_NOT_REGISTERED);
    }
}
