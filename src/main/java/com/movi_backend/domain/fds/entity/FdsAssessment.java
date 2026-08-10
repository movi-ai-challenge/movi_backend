package com.movi_backend.domain.fds.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 이체 1건에 대한 위험도 평가. 이체당 1건만 존재한다.
 *
 * <p>{@code features}에 모델 입력 스냅샷을 남긴다. 모델을 교체한 뒤 과거 거래를 재평가하거나
 * 백테스트할 때 필요하다.
 *
 * <p><b>평가에 실패하면 이체를 통과시키지 않는다.</b> 평가 불가는 곧 위험이다.
 */
@Getter
@Entity
@Table(name = "fds_assessments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FdsAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assessment_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 예: isolation-forest-v1 */
    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "anomaly_score", nullable = false, precision = 10, scale = 6)
    private BigDecimal anomalyScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private FdsDecision decision;

    /** 모델 입력 피처 스냅샷 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features")
    private String features;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @Builder
    private FdsAssessment(
            final Transfer transfer,
            final User user,
            final String modelVersion,
            final BigDecimal anomalyScore,
            final RiskLevel riskLevel,
            final FdsDecision decision,
            final String features,
            final Integer latencyMs
    ) {
        this.transfer = transfer;
        this.user = user;
        this.modelVersion = modelVersion;
        this.anomalyScore = anomalyScore;
        this.riskLevel = riskLevel;
        this.decision = decision;
        this.features = features;
        this.latencyMs = latencyMs;
        this.evaluatedAt = LocalDateTime.now();
    }

    /** 보호자에게 알림을 보내야 하는 평가인지 여부 */
    public boolean requiresGuardianAlert() {
        return this.decision.requiresGuardianAlert();
    }
}
