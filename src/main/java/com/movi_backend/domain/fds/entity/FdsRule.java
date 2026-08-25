package com.movi_backend.domain.fds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FDS 룰. 초기 데이터는 docs/schema.sql의 INSERT로 적재한다.
 *
 * <p>모델 점수만으로는 왜 위험한지 설명할 수 없어, 룰 매칭 결과를 함께 남겨
 * 보호자·상담원이 판단 근거를 확인할 수 있게 한다.
 */
@Getter
@Entity
@Table(name = "fds_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FdsRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long id;

    /** 예: FIRST_TIME_RECIPIENT */
    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    /** SpEL 등 조건 표현식 */
    @Column(name = "condition_expr", nullable = false, length = 500)
    private String conditionExpr;

    @Column(name = "risk_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskWeight;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private FdsRule(
            final String ruleCode,
            final String description,
            final String conditionExpr,
            final BigDecimal riskWeight
    ) {
        this.ruleCode = ruleCode;
        this.description = description;
        this.conditionExpr = conditionExpr;
        this.riskWeight = riskWeight;
        this.active = true;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
