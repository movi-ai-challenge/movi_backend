package com.movi_backend.domain.guardian.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
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
 * 피보호자-보호자 연결.
 *
 * <p>회원가입 시 입력한 보호자 전화번호로 확인 절차 없이 바로 생성된다. {@code guardianUser}는
 * nullable이다 — 보호자가 Movi에 가입한 회원이 아니어도 전화번호만으로 연결되며, 알림은
 * {@code guardianPhone}으로 발송한다.
 *
 * <p><b>보호자에게 이체 승인 권한은 없다.</b> MVP에서 사전 차단 기능은 제외했고 알림만 받는다.
 *
 * <p>{@code guardianPhone}은 무작위 IV로 암호화하므로 같은 번호라도 매번 다른 암호문이 나온다.
 * 즉 <b>암호문 비교로는 중복 등록을 판별할 수 없다.</b> 중복 검사는 {@code guardianPhoneHash}
 * (HMAC-SHA256)로 한다. {@code users.phone_hash}와 같은 원칙이다.
 */
@Getter
@Entity
@Table(name = "guardian_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;

    /** 피보호자 (앱 주 사용자) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protectee_user_id", nullable = false)
    private User protecteeUser;

    /** 보호자. Movi 회원이 아니면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id")
    private User guardianUser;

    @Column(name = "guardian_name", nullable = false, length = 50)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false, length = 255)
    private String guardianPhone;

    /** 중복 초대 확인용 HMAC-SHA256. 검색 전용이며 복호화 대상이 아니다. */
    @Column(name = "guardian_phone_hash", length = 64)
    private String guardianPhoneHash;

    /** 자녀/배우자/사회복지사 등 */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation", length = 30)
    private GuardianRelation relation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GuardianLinkStatus status;

    /** 예: {"view_balance":true,"receive_alert":true} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permission_scope")
    private String permissionScope;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Builder
    private GuardianLink(
            final User protecteeUser,
            final String guardianName,
            final String guardianPhone,
            final String guardianPhoneHash,
            final GuardianRelation relation,
            final String permissionScope
    ) {
        this.protecteeUser = protecteeUser;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.guardianPhoneHash = guardianPhoneHash;
        this.relation = relation;
        this.permissionScope = permissionScope;
        this.status = GuardianLinkStatus.ACTIVE;
        this.linkedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == GuardianLinkStatus.ACTIVE;
    }

    public void revoke() {
        this.status = GuardianLinkStatus.REVOKED;
    }
}
