package com.movi_backend.domain.auth.entity;

import com.movi_backend.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 기기.
 *
 * <p>{@code trusted}는 FDS 피처로 쓰인다. 미등록 기기에서의 이체는 위험 신호이므로
 * 신규 기기 로그인 시 반드시 기록한다.
 */
@Getter
@Entity
@Table(name = "devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_uuid", nullable = false, length = 100)
    private String deviceUuid;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "push_token", length = 512)
    private String pushToken;

    @Column(name = "is_trusted", nullable = false)
    private boolean trusted;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    private Device(
            final User user,
            final String deviceUuid,
            final String deviceModel,
            final String osVersion,
            final String pushToken
    ) {
        this.user = user;
        this.deviceUuid = deviceUuid;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
        this.pushToken = pushToken;
        this.trusted = false;
    }

    public void recordLogin(final LocalDateTime now) {
        this.lastLoginAt = now;
    }

    public void trust() {
        this.trusted = true;
    }

    public void updatePushToken(final String pushToken) {
        this.pushToken = pushToken;
    }
}
