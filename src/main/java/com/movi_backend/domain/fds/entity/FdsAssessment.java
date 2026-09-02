package com.movi_backend.domain.fds.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import jakarta.persistence.Column;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

    /**
     * 이 평가가 짚은 근거 코드.
     *
     * <p>{@code features} JSON 안에 이미 저장돼 있어 컬럼을 따로 두지 않는다. 이체를
     * 다시 조회하는 경로(멱등 재요청 등)에서도 같은 근거를 꺼낼 수 있어야, 사용자가
     * 두 번 물어봤을 때 다른 이유를 듣지 않는다.
     *
     * <p>읽지 못하면 빈 목록이다. 근거를 못 읽는 것이 이체 조회를 실패시킬 이유는 아니다.
     */
    public List<String> readReasonCodes(final ObjectMapper objectMapper) {
        if (this.features == null || this.features.isBlank()) {
            return List.of();
        }
        try {
            final JsonNode codes = objectMapper.readTree(this.features).path("reasonCodes");
            if (!codes.isArray()) {
                return List.of();
            }
            final List<String> parsed = new ArrayList<>();
            codes.forEach(node -> parsed.add(node.asString()));
            return List.copyOf(parsed);
        } catch (final RuntimeException exception) {
            return List.of();
        }
    }
}
