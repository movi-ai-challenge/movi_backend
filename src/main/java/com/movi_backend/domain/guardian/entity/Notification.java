package com.movi_backend.domain.guardian.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.transfer.entity.Transfer;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 발송 이력.
 *
 * <p>보호자 승인이 빠지면서 알림이 이상거래에 대한 유일한 대응 수단이 됐다. 그래서
 * {@code transfer}를 직접 참조해 "어떤 이체 때문에 나간 알림인지" 역추적할 수 있게 한다.
 *
 * <p>{@code user}는 nullable이다. 미가입 보호자에게는 전화번호로만 보낸다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    /** 수신자. 미가입 보호자면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id")
    private GuardianLink guardianLink;

    /** 이 알림을 유발한 이체 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /** GUARDIAN_INVITE / RISK_TRANSFER_ALERT / BLOCKED_TRANSFER_ALERT */
    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Column(name = "target_phone", length = 255)
    private String targetPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_msg_id", length = 100)
    private String providerMsgId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    private Notification(
            final User user,
            final GuardianLink guardianLink,
            final Transfer transfer,
            final NotificationChannel channel,
            final String templateCode,
            final String targetPhone,
            final String payload
    ) {
        this.user = user;
        this.guardianLink = guardianLink;
        this.transfer = transfer;
        this.channel = channel;
        this.templateCode = templateCode;
        this.targetPhone = targetPhone;
        this.payload = payload;
        this.status = NotificationStatus.QUEUED;
    }

    public void markSent(final String providerMsgId, final LocalDateTime now) {
        this.status = NotificationStatus.SENT;
        this.providerMsgId = providerMsgId;
        this.sentAt = now;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
