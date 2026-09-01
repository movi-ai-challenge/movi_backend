package com.movi_backend.domain.auth.application;

import com.movi_backend.domain.auth.application.event.TrustedDeviceRegistrationRequested;
import com.movi_backend.domain.auth.dto.request.PasswordLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinRegisterRequest;
import com.movi_backend.domain.auth.dto.request.SignUpRequest;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.entity.UserCredential;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.auth.repository.UserCredentialRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.JwtTokenProvider;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 일반 회원가입. 카카오를 거치지 않고 계정을 만들고 곧바로 로그인 상태로 만든다.
     *
     * <p>가입 직후 토큰을 함께 내려 주는 이유는, 화면을 보지 않는 사용자에게 "가입됐으니
     * 이제 로그인하세요"라는 두 번째 입력 단계를 요구하지 않기 위해서다.
     */
    @Transactional
    public LoginResponse signUp(final SignUpRequest request) {
        final String loginId = normalizeLoginId(request.loginId());
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_REGISTERED);
        }

        final User user = User.builder()
                .name(request.name())
                .loginId(loginId)
                .userType(UserType.GENERAL)
                .build();
        userRepository.save(user);

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            registerPhone(user, request.phoneNumber());
        }

        userCredentialRepository.save(UserCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(request.password()))
                .biometricEnabled(false)
                .build());

        eventPublisher.publishEvent(new TrustedDeviceRegistrationRequested(
                user.getId(),
                request.deviceUuid(),
                request.deviceModel(),
                request.osVersion()
        ));

        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(user));
        return LoginResponse.of(user.getId(), user.getName(), true, tokenPair);
    }

    /**
     * 일반 로그인(아이디 + 비밀번호).
     *
     * <p>아이디가 없을 때도 비밀번호가 틀렸을 때와 같은 {@code PASSWORD_MISMATCH}를 던진다.
     * 응답이 갈리면 어떤 아이디가 가입돼 있는지 밖에서 확인할 수 있다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse loginWithPassword(final PasswordLoginRequest request) {
        final User user = userRepository.findByLoginId(normalizeLoginId(request.loginId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_MISMATCH));
        validateActive(user);

        final UserCredential credential = userCredentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_NOT_REGISTERED));
        verifyPassword(credential, request.password());
        eventPublisher.publishEvent(new TrustedDeviceRegistrationRequested(
                user.getId(),
                request.deviceUuid(),
                request.deviceModel(),
                request.osVersion()
        ));

        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(user));
        return LoginResponse.of(user.getId(), user.getName(), false, tokenPair);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse loginWithPin(final PinLoginRequest request) {
        final String normalizedPhone = normalizePhone(request.phoneNumber());
        final String phoneHash = sensitiveDataCrypto.hash(normalizedPhone);
        final User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.PIN_MISMATCH));
        validateActive(user);

        final UserCredential credential = userCredentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PIN_NOT_REGISTERED));
        verifyPin(credential, request.pin());
        eventPublisher.publishEvent(new TrustedDeviceRegistrationRequested(
                user.getId(),
                request.deviceUuid(),
                request.deviceModel(),
                request.osVersion()
        ));

        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(user));
        return LoginResponse.of(user.getId(), user.getName(), false, tokenPair);
    }

    @Transactional
    /**
     * PIN 최초 등록. 카카오 가입 시점에는 전화번호를 받지 않으므로, PIN 로그인이 사용할
     * 전화번호를 이 시점에 함께 받아 {@code users.phone}을 채운다.
     */
    public void registerPin(final Long userId, final PinRegisterRequest request) {
        final User user = findActiveUser(userId);
        registerPhone(user, request.phoneNumber());
        upsertPin(user, request.pin());
        eventPublisher.publishEvent(new TrustedDeviceRegistrationRequested(
                user.getId(),
                request.deviceUuid(),
                request.deviceModel(),
                request.osVersion()
        ));
    }

    /** 다른 계정이 이미 쓰는 번호는 거부한다. 보호자 알림이 엉뚱한 사람에게 갈 수 있다. */
    private void registerPhone(final User user, final String phoneNumber) {
        final String normalizedPhone = PhoneNumberNormalizer.normalize(phoneNumber);
        final String phoneHash = sensitiveDataCrypto.hash(normalizedPhone);
        userRepository.findByPhoneHash(phoneHash).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
            }
        });
        user.registerPhone(sensitiveDataCrypto.encrypt(normalizedPhone), phoneHash);
    }

    @Transactional(readOnly = true)
    public JwtTokenPair refresh(final String refreshToken) {
        final AuthUser tokenUser = jwtTokenProvider.parseRefreshToken(refreshToken);
        final User user = userRepository.findById(tokenUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!user.isActive() || tokenUser.tokenVersion() != user.getTokenVersion()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return jwtTokenProvider.issueTokenPair(toAuthUser(user));
    }

    @Transactional
    public void logout(final Long userId) {
        final User user = findActiveUser(userId);
        user.invalidateTokens();
    }

    private void verifyPin(final UserCredential credential, final String pin) {
        if (!credential.hasPin()) {
            throw new BusinessException(ErrorCode.PIN_NOT_REGISTERED);
        }
        final LocalDateTime now = LocalDateTime.now();
        if (credential.isLocked(now)) {
            throw new BusinessException(ErrorCode.PIN_LOCKED);
        }
        if (passwordEncoder.matches(pin, credential.getPinHash())) {
            credential.recordSuccess();
            return;
        }
        credential.recordFailure(now);
        if (credential.isLocked(now)) {
            throw new BusinessException(ErrorCode.PIN_LOCKED);
        }
        throw new BusinessException(ErrorCode.PIN_MISMATCH);
    }

    /**
     * PIN 을 등록한다.
     *
     * <p><b>자격증명 행의 존재만으로 "이미 등록됨"을 판단하지 않는다.</b> 일반 회원가입을 하면
     * 비밀번호만 담긴 행이 먼저 생기는데, 그 행을 보고 거절하면 일반 가입자는 PIN 을 영영
     * 등록할 수 없다. 실제로 {@code pin_hash} 가 차 있을 때만 거절하고, 비밀번호만 있는
     * 행에는 PIN 을 채워 넣는다. 한 사용자가 두 수단을 함께 쓸 수 있어야 한다.
     */
    private void upsertPin(final User user, final String pin) {
        final String pinHash = passwordEncoder.encode(pin);
        final UserCredential existing = userCredentialRepository.findByUserId(user.getId())
                .orElse(null);
        if (existing == null) {
            userCredentialRepository.save(UserCredential.builder()
                    .user(user)
                    .pinHash(pinHash)
                    .biometricEnabled(false)
                    .build());
            return;
        }
        if (existing.hasPin()) {
            throw new BusinessException(ErrorCode.PIN_ALREADY_REGISTERED);
        }
        existing.changePin(pinHash);
    }

    /**
     * 비밀번호 검증. 실패 횟수와 잠금은 PIN과 같은 레코드를 쓴다 — 계정 단위 잠금이라
     * 수단을 바꿔 가며 시도 횟수를 늘릴 수 없다.
     */
    private void verifyPassword(final UserCredential credential, final String password) {
        if (!credential.hasPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_REGISTERED);
        }
        final LocalDateTime now = LocalDateTime.now();
        if (credential.isLocked(now)) {
            throw new BusinessException(ErrorCode.PASSWORD_LOCKED);
        }
        if (passwordEncoder.matches(password, credential.getPasswordHash())) {
            credential.recordSuccess();
            return;
        }
        credential.recordFailure(now);
        if (credential.isLocked(now)) {
            throw new BusinessException(ErrorCode.PASSWORD_LOCKED);
        }
        throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
    }

    /** 대소문자를 구분하지 않는다. Movi 로 가입한 사람이 movi 로 로그인해도 같은 계정이어야 한다. */
    private String normalizeLoginId(final String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }

    private User findActiveUser(final Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActive(user);
        return user;
    }

    private void validateActive(final User user) {
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private AuthUser toAuthUser(final User user) {
        return AuthUser.of(user.getId(), user.getUserType(), user.getTokenVersion());
    }

    private String normalizePhone(final String phoneNumber) {
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
            throw new BusinessException(ErrorCode.PIN_MISMATCH);
        }
        return normalized;
    }
}
