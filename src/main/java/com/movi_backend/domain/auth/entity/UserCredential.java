package com.movi_backend.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PIN·생체 인증 정보. 회원 1명당 1건이다.
 *
 * <p><b>잠금 중에는 검증 자체를 건너뛰고 즉시 거부한다.</b> 올바른 값이 들어와도 마찬가지다.
 *
 * <p>실패 횟수와 잠금은 PIN과 비밀번호가 <b>함께</b> 쓴다. 한 계정에 대한 잠금이지 수단별 잠금이
 * 아니므로, 비밀번호를 5회 틀리면 PIN 로그인도 같이 막힌다. 공격자가 수단을 바꿔가며
 * 시도 횟수를 늘리는 것을 막기 위해서다.
 */
@Getter
@Entity
@Table(name = "user_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredential {

    /** 연속 실패 허용 횟수. 초과하면 잠금 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** 잠금 지속 시간(분) */
    public static final int LOCK_DURATION_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credential_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 카카오 가입자가 등록한 PIN. 일반 회원가입만 한 사용자는 PIN이 없어 {@code null}이다.
     */
    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    /**
     * 일반 로그인 비밀번호. 카카오·PIN만 쓰는 사용자는 {@code null}이다.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "pin_updated_at", nullable = false)
    private LocalDateTime pinUpdatedAt;

    @Builder
    private UserCredential(
            final User user,
            final String pinHash,
            final String passwordHash,
            final boolean biometricEnabled
    ) {
        this.user = user;
        this.pinHash = pinHash;
        this.passwordHash = passwordHash;
        this.biometricEnabled = biometricEnabled;
        this.failedAttempts = 0;
        this.pinUpdatedAt = LocalDateTime.now();
    }

    /** 지정 시각 기준으로 잠금 상태인지 여부 */
    public boolean isLocked(final LocalDateTime now) {
        if (this.lockedUntil == null) {
            return false;
        }
        return now.isBefore(this.lockedUntil);
    }

    /** 검증 실패를 기록한다. 허용 횟수를 넘기면 잠근다. */
    public void recordFailure(final LocalDateTime now) {
        this.failedAttempts++;
        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = now.plusMinutes(LOCK_DURATION_MINUTES);
        }
    }

    /** 검증 성공 시 실패 횟수와 잠금을 초기화한다. */
    public void recordSuccess() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void changePin(final String pinHash) {
        this.pinHash = pinHash;
        this.pinUpdatedAt = LocalDateTime.now();
        recordSuccess();
    }

    public void changePassword(final String passwordHash) {
        this.passwordHash = passwordHash;
        recordSuccess();
    }

    public boolean hasPin() {
        return this.pinHash != null;
    }

    public boolean hasPassword() {
        return this.passwordHash != null;
    }

    public void changeBiometricEnabled(final boolean enabled) {
        this.biometricEnabled = enabled;
    }
}
