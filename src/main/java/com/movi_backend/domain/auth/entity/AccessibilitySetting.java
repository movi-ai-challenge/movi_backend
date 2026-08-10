package com.movi_backend.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 접근성 설정. 회원 1명당 1건이다.
 *
 * <p>{@code voiceOnlyMode}는 화면을 전혀 쓰지 않는 완전 비시각 모드다.
 */
@Getter
@Entity
@Table(name = "accessibility_settings")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessibilitySetting {

    private static final BigDecimal DEFAULT_SCALE = BigDecimal.valueOf(1.00);
    private static final String DEFAULT_VOICE = "DEFAULT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TTS 재생 속도. 0.50 ~ 2.00 */
    @Column(name = "tts_speed", nullable = false, precision = 3, scale = 2)
    private BigDecimal ttsSpeed;

    @Column(name = "tts_voice", nullable = false, length = 50)
    private String ttsVoice;

    @Column(name = "font_scale", nullable = false, precision = 3, scale = 2)
    private BigDecimal fontScale;

    @Column(name = "high_contrast", nullable = false)
    private boolean highContrast;

    @Column(name = "haptic_enabled", nullable = false)
    private boolean hapticEnabled;

    @Column(name = "voice_only_mode", nullable = false)
    private boolean voiceOnlyMode;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private AccessibilitySetting(final User user) {
        this.user = user;
        this.ttsSpeed = DEFAULT_SCALE;
        this.ttsVoice = DEFAULT_VOICE;
        this.fontScale = DEFAULT_SCALE;
        this.highContrast = false;
        this.hapticEnabled = true;
        this.voiceOnlyMode = false;
    }

    public void changeTtsSpeed(final BigDecimal ttsSpeed) {
        this.ttsSpeed = ttsSpeed;
    }

    public void changeFontScale(final BigDecimal fontScale) {
        this.fontScale = fontScale;
    }

    public void changeDisplayOptions(final boolean highContrast, final boolean voiceOnlyMode) {
        this.highContrast = highContrast;
        this.voiceOnlyMode = voiceOnlyMode;
    }

    public void changeHapticEnabled(final boolean hapticEnabled) {
        this.hapticEnabled = hapticEnabled;
    }
}
