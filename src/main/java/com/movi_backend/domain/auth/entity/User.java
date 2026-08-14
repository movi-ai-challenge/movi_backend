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

    @Column(name = "phone", nullable = false, length = 255)
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
