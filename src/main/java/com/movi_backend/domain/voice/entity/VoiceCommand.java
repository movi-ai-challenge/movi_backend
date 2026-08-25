package com.movi_backend.domain.voice.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.voice.type.VoiceCommandStatus;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.global.entity.BaseCreatedEntity;
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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 음성 명령 1건.
 *
 * <p>STT·NLU 결과는 AI 파트가 넘겨주지만 <b>백엔드가 검증 주체</b>다. 엔티티가 비어 있으면
 * 추측해서 채우지 말고 {@link VoiceCommandStatus#CLARIFY}로 기록한 뒤 재질문한다.
 *
 * <p>{@code sttConfidence}는 FDS 피처로 전달된다. 인식 신뢰도가 낮은 이체는 위험 신호다.
 */
@Getter
@Entity
@Table(name = "voice_commands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceCommand extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "command_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VoiceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "audio_uri", length = 500)
    private String audioUri;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Column(name = "stt_confidence", precision = 5, scale = 4)
    private BigDecimal sttConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, length = 40)
    private VoiceIntent intent;

    /** 추출 엔티티 스냅샷. 예: {"recipient":"엄마","amount":50000} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entities")
    private String entities;

    @Column(name = "nlu_confidence", precision = 5, scale = 4)
    private BigDecimal nluConfidence;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VoiceCommandStatus status;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Builder
    private VoiceCommand(
            final VoiceSession session,
            final User user,
            final String audioUri,
            final String sttText,
            final BigDecimal sttConfidence,
            final VoiceIntent intent,
            final String entities,
            final BigDecimal nluConfidence
    ) {
        this.session = session;
        this.user = user;
        this.audioUri = audioUri;
        this.sttText = sttText;
        this.sttConfidence = sttConfidence;
        this.intent = intent;
        this.entities = entities;
        this.nluConfidence = nluConfidence;
        this.status = VoiceCommandStatus.SUCCESS;
    }

    /** 필수 슬롯이 비어 재질문한 경우 */
    public void markClarify(final String responseText) {
        this.status = VoiceCommandStatus.CLARIFY;
        this.responseText = responseText;
    }

    public void markFailed(final String responseText) {
        this.status = VoiceCommandStatus.FAILED;
        this.responseText = responseText;
    }

    public void completeWith(final String responseText, final int processingMs) {
        this.status = VoiceCommandStatus.SUCCESS;
        this.responseText = responseText;
        this.processingMs = processingMs;
    }
}
