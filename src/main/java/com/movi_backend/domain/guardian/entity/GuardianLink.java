package com.movi_backend.domain.guardian.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
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
 * <p>{@code guardianUser}는 nullable이다. SMS 초대 시점에 보호자는 아직 미가입 상태이므로
 * 전화번호로 먼저 식별하고, 수락 시점에 {@link #accept(User, LocalDateTime)}로 바인딩한다.
 *
 * <p><b>보호자에게 이체 승인 권한은 없다.</b> MVP에서 사전 차단 기능은 제외했고 알림만 받는다.
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

    /** 보호자. 초대 수락 전에는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id")
    private User guardianUser;

    @Column(name = "guardian_name", nullable = false, length = 50)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false, length = 255)
    private String guardianPhone;

    /** 자녀/배우자/사회복지사 등 */
    @Column(name = "relation", length = 30)
    private String relation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GuardianLinkStatus status;

    @Column(name = "invite_token", nullable = false, length = 64)
    private String inviteToken;

    @Column(name = "invite_expires_at", nullable = false)
    private LocalDateTime inviteExpiresAt;

    /** 예: {"view_balance":true,"receive_alert":true} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permission_scope")
    private String permissionScope;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Builder
    private GuardianLink(
            final User protecteeUser,
            final String guardianName,
            final String guardianPhone,
            final String relation,
            final String inviteToken,
            final LocalDateTime inviteExpiresAt,
            final String permissionScope
    ) {
        this.protecteeUser = protecteeUser;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.relation = relation;
        this.inviteToken = inviteToken;
        this.inviteExpiresAt = inviteExpiresAt;
        this.permissionScope = permissionScope;
        this.status = GuardianLinkStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    public boolean isInviteExpired(final LocalDateTime now) {
        return now.isAfter(this.inviteExpiresAt);
    }

    public boolean isActive() {
        return this.status == GuardianLinkStatus.ACTIVE;
    }

    /** 보호자가 초대를 수락한다. 가입한 회원 계정을 이 시점에 연결한다. */
    public void accept(final User guardianUser, final LocalDateTime now) {
        this.guardianUser = guardianUser;
        this.status = GuardianLinkStatus.ACTIVE;
        this.acceptedAt = now;
    }

    /**
     * 초대·승인 없이 바로 연결을 성립시킨다.
     *
     * <p>이용자가 본인 계정에 보호자 번호를 직접 등록하는 경로다. 보호자 확인 화면이 없으므로
     * 초대 토큰을 발급해 기다릴 대상이 없다. {@code guardianUser}는 그대로 {@code null}로 둔다 —
     * 보호자가 Movi 회원이 아니어도 전화번호만으로 알림을 받기 때문이다.
     */
    public void activateWithoutInvite(final LocalDateTime now) {
        this.status = GuardianLinkStatus.ACTIVE;
        this.acceptedAt = now;
    }

    public void reject() {
        this.status = GuardianLinkStatus.REJECTED;
    }

    public void revoke() {
        this.status = GuardianLinkStatus.REVOKED;
    }
}
