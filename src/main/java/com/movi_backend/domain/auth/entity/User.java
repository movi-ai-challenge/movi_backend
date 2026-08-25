package com.movi_backend.domain.auth.entity;

import com.movi_backend.domain.auth.type.UserStatus;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 사용자.
 *
 * <p>{@code phone}은 AES 암호화 대상이며 로그에 원문으로 남기지 않는다.
 *
 * <p>카카오 최초 가입 시점에는 {@code phone}이 없다 — 카카오는 회원 정보로 받지 않는다.
 * PIN 로그인이 전화번호로 사용자를 찾으므로, PIN을 등록하는 시점({@link #registerPhone})에
 * 전화번호를 받아 채운다. PIN을 등록하기 전까지는 {@code phone}·{@code phoneHash}가 {@code null}이다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", length = 255)
    private String phone;

    @Column(name = "phone_hash", length = 64, unique = true)
    private String phoneHash;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 30)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Builder
    private User(
            final String name,
            final String phone,
            final String phoneHash,
            final LocalDate birthDate,
            final UserType userType
    ) {
        this.name = name;
        this.phone = phone;
        this.phoneHash = phoneHash;
        this.birthDate = birthDate;
        this.userType = userType;
        this.status = UserStatus.ACTIVE;
        this.tokenVersion = 0L;
    }

    public void changeUserType(final UserType userType) {
        this.userType = userType;
    }

    /** PIN 등록 시점에 전화번호를 채운다. PIN 로그인이 이 값으로 사용자를 찾는다. */
    public void registerPhone(final String encryptedPhone, final String phoneHash) {
        this.phone = encryptedPhone;
        this.phoneHash = phoneHash;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public void invalidateTokens() {
        this.tokenVersion++;
    }
}
