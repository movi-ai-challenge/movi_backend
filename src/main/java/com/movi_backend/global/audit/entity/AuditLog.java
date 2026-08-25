package com.movi_backend.global.audit.entity;

import com.movi_backend.global.audit.type.ActorType;
import com.movi_backend.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 감사 로그.
 *
 * <p>금융 거래 이력 추적용이다. 사용자를 {@code userId} 값으로만 들고 있는 이유는,
 * 회원이 탈퇴해도 로그는 남아야 하고 조회 성능상 연관관계가 필요 없기 때문이다.
 *
 * <p><b>{@code detail}에 계좌번호·전화번호·인증 토큰을 넣지 않는다.</b>
 */
@Getter
@Entity
@Table(name = "audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;

    @Builder
    private AuditLog(
            final Long userId,
            final ActorType actorType,
            final String action,
            final String resourceType,
            final Long resourceId,
            final String ip,
            final String userAgent,
            final String detail
    ) {
        this.userId = userId;
        this.actorType = actorType;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.detail = detail;
    }
}
