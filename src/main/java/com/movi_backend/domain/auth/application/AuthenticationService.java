package com.movi_backend.domain.auth.application;

import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.entity.UserCredential;
import com.movi_backend.domain.auth.repository.UserCredentialRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.global.security.JwtTokenProvider;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
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

        final JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(toAuthUser(user));
        return LoginResponse.of(user.getId(), false, tokenPair);
    }

    @Transactional
    public void registerPin(final Long userId, final String pin) {
        final User user = findActiveUser(userId);
        if (userCredentialRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.PIN_ALREADY_REGISTERED);
        }
        final UserCredential credential = UserCredential.builder()
                .user(user)
                .pinHash(passwordEncoder.encode(pin))
                .biometricEnabled(false)
                .build();
        userCredentialRepository.save(credential);
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
