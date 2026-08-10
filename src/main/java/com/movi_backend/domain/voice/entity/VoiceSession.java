package com.movi_backend.domain.voice.entity;

import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.voice.type.VoiceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 음성 대화 세션.
 *
 * <p>재질문으로 이어지는 멀티턴 대화의 단위다. 앞선 발화에서 추출한 슬롯을 이 세션에 보관한다.
 */
@Getter
@Entity
@Table(name = "voice_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private VoiceChannel channel;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Builder
    private VoiceSession(final User user, final Device device, final VoiceChannel channel) {
        this.user = user;
        this.device = device;
        this.channel = channel;
        this.startedAt = LocalDateTime.now();
    }

    public void end(final LocalDateTime now) {
        this.endedAt = now;
    }

    public boolean isEnded() {
        return this.endedAt != null;
    }
}
