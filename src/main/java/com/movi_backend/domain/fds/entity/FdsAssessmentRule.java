package com.movi_backend.domain.fds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 평가 1건에서 각 룰이 매칭됐는지와 점수 기여도.
 *
 * <p>"왜 차단됐는지"를 설명하는 근거가 된다.
 */
@Getter
@Entity
@Table(name = "fds_assessment_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FdsAssessmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private FdsAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private FdsRule rule;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "contribution", precision = 10, scale = 6)
    private BigDecimal contribution;

    @Builder
    private FdsAssessmentRule(
            final FdsAssessment assessment,
            final FdsRule rule,
            final boolean matched,
            final BigDecimal contribution
    ) {
        this.assessment = assessment;
        this.rule = rule;
        this.matched = matched;
        this.contribution = contribution;
    }
}
