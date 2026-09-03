package com.movi_backend.domain.voice.entity;

import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.voice.type.VoiceChannel;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 음성 대화 세션.
 *
 * <p><b>백엔드가 슬롯의 단일 소유자다.</b> 앞선 발화에서 추출한 값을 여기에 보관하고,
 * 만료·병합·폐기를 모두 이 엔티티가 책임진다. 프론트와 AI는 슬롯을 보관하지 않는다.
 *
 * <p>만료 정책 (docs/integration-spec.md 6.2절)
 * <ul>
 *   <li>일반 세션: 마지막 활동 후 5분</li>
 *   <li>누락 슬롯 재질문: 마지막 재질문 후 180초</li>
 *   <li>확인 대기: 확인 문장 생성 후 180초</li>
 *   <li>같은 슬롯 재질문: 최대 3회</li>
 * </ul>
 *
 * <p>만료된 슬롯은 <b>일부만 살리지 않고 전부 폐기</b>한다. 오래된 슬롯이 남아 있으면
 * 엉뚱한 이체가 나가기 때문이다.
 */
@Getter
@Entity
@Table(name = "voice_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceSession {

    /** 마지막 활동 후 세션 유효시간(분) */
    public static final int SESSION_TIMEOUT_MINUTES = 5;

    /**
     * 재질문·확인 대기 유효시간(초).
     *
     * <p>60초였다. 화면을 보지 않는 사용자에게는 그 안에 끝나지 않는다 -- 확인 문장
     * 낭독 5초, 마이크를 더듬어 찾는 데 10초, 녹음이 저절로 멈추기를 기다리는 데
     * 최대 15초, 업로드와 STT·GPT 분석에 15~20초다. 한 번만 되물어도 넘긴다.
     *
     * <p>슬롯이 오래 살아 있는 것은 그 자체로 위험하지만, 확인 대기는 사용자가 이미
     * 확인 문장을 들은 상태이고 이체 실행에는 confirmationId 대조가 따로 걸린다.
     * 만료로 매번 처음부터 다시 말하게 하는 쪽이 실제로는 더 나쁘다.
     */
    public static final int PENDING_TIMEOUT_SECONDS = 180;

    /** 같은 슬롯 재질문 허용 횟수 */
    public static final int MAX_RETRY_COUNT = 3;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VoiceSessionStatus status;

    /** 재질문·확인 대기 중인 의도 */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_intent", length = 40)
    private VoiceIntent pendingIntent;

    /** 지금까지 채워진 슬롯. 예: {"recipient":"엄마","amount":null} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_slots")
    private String pendingSlots;

    /** 같은 슬롯을 다시 물어본 횟수 */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Builder
    private VoiceSession(final User user, final Device device, final VoiceChannel channel) {
        final LocalDateTime now = LocalDateTime.now();
        this.user = user;
        this.device = device;
        this.channel = channel;
        this.status = VoiceSessionStatus.ACTIVE;
        this.retryCount = 0;
        this.startedAt = now;
        this.expiresAt = now.plusMinutes(SESSION_TIMEOUT_MINUTES);
    }

    /**
     * 만료 여부. 만료 시각과 <b>같은 순간도 만료로 본다</b> — 경계에서 슬롯이 살아남아
     * 다음 발화에 섞이는 것을 막기 위해 보수적으로 판정한다.
     */
    public boolean isExpired(final LocalDateTime now) {
        return !now.isBefore(this.expiresAt);
    }

    public boolean isClosed() {
        return this.status.isClosed();
    }

    /** 재질문 횟수가 허용치를 넘었는지 여부 */
    public boolean isRetryExceeded() {
        return this.retryCount >= MAX_RETRY_COUNT;
    }

    /**
     * 필수 슬롯이 비어 재질문한다. 채워진 슬롯을 보관하고 유효시간을 180초로 잡는다.
     * 같은 슬롯을 다시 물어보는 것이므로 재질문 횟수를 올린다.
     */
    public void clarify(
            final VoiceIntent intent,
            final String pendingSlots,
            final LocalDateTime now
    ) {
        transitionTo(VoiceSessionStatus.CLARIFYING);
        this.pendingIntent = intent;
        this.pendingSlots = pendingSlots;
        this.retryCount++;
        this.expiresAt = now.plusSeconds(PENDING_TIMEOUT_SECONDS);
    }

    /**
     * 확인 문장을 읽어 주고 사용자 응답을 기다린다.
     *
     * <p>{@code intent}를 함께 보관한다. 이어지는 발화는 {@code CONFIRM}/{@code CANCEL}이라
     * 그 자체로는 무엇을 확정하는지 알 수 없기 때문이다.
     */
    public void awaitConfirmation(
            final VoiceIntent intent,
            final String pendingSlots,
            final LocalDateTime now
    ) {
        transitionTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
        this.pendingIntent = intent;
        this.pendingSlots = pendingSlots;
        this.retryCount = 0;
        this.expiresAt = now.plusSeconds(PENDING_TIMEOUT_SECONDS);
    }

    /**
     * 확인을 받아 이체 처리를 시작한다. 확인 대기 상태에서만 진입할 수 있고,
     * 이 상태에서는 확인 발화를 다시 받지 않는다.
     */
    public void startProcessing(final LocalDateTime now) {
        transitionTo(VoiceSessionStatus.PROCESSING);
        this.expiresAt = now.plusMinutes(SESSION_TIMEOUT_MINUTES);
    }

    /**
     * 의도가 바뀌어 명령 대기로 돌아간다.
     *
     * <p>송금 슬롯을 채우는 중에 사용자가 거래내역을 물으면 앞선 송금은 포기된 것으로 본다.
     * 슬롯을 남겨 두면 뒤이은 발화가 옛 슬롯과 병합돼 사용자가 의도하지 않은 이체가 나간다.
     * 이미 {@code ACTIVE}면 유효시간만 늘린다.
     */
    public void resumeActive(final LocalDateTime now) {
        if (this.status != VoiceSessionStatus.ACTIVE) {
            transitionTo(VoiceSessionStatus.ACTIVE);
            clearSlots();
        }
        this.expiresAt = now.plusMinutes(SESSION_TIMEOUT_MINUTES);
    }

    public void complete(final LocalDateTime now) {
        transitionTo(VoiceSessionStatus.COMPLETED);
        clearSlots();
        this.endedAt = now;
    }

    public void cancel(final LocalDateTime now) {
        transitionTo(VoiceSessionStatus.CANCELED);
        clearSlots();
        this.endedAt = now;
    }

    public void expire(final LocalDateTime now) {
        transitionTo(VoiceSessionStatus.EXPIRED);
        clearSlots();
        this.endedAt = now;
    }

    private void transitionTo(final VoiceSessionStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new BusinessException(
                    ErrorCode.INVALID_SESSION_STATE,
                    "%s -> %s".formatted(this.status, next)
            );
        }
        this.status = next;
    }

    /** 슬롯을 전부 폐기한다. 일부만 살리지 않는다. */
    private void clearSlots() {
        this.pendingIntent = null;
        this.pendingSlots = null;
        this.retryCount = 0;
    }
}
