package com.movi_backend.domain.transfer.entity;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.entity.VoiceCommand;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이체 요청.
 *
 * <p><b>멱등성이 필수다.</b> 음성은 오인식·중복 발화가 잦으므로 클라이언트가 발급한
 * {@code idempotencyKey}로 중복 이체를 차단한다.
 *
 * <p>상태 전이는 {@link TransferStatus#canTransitionTo}가 강제한다. 특히 {@code COMPLETED}
 * 이후에는 어떤 상태로도 가지 않는다. 또한 <b>모든 이체는 FDS 평가를 거쳐야</b> 완료될 수 있다.
 */
@Getter
@Entity
@Table(
        name = "transfers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transfer_user_idem",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private TransferRecipient recipient;

    /** 이 이체를 유발한 음성 명령. 화면 조작으로 요청했다면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_command_id")
    private VoiceCommand voiceCommand;

    @Column(name = "to_bank_code", nullable = false, length = 10)
    private String toBankCode;

    @Column(name = "to_account_num", nullable = false, length = 255)
    private String toAccountNum;

    @Column(name = "to_holder_name", nullable = false, length = 50)
    private String toHolderName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransferStatus status;

    /** 중복 발화 방지 키 */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private Transfer(
            final User user,
            final Account fromAccount,
            final TransferRecipient recipient,
            final VoiceCommand voiceCommand,
            final String toBankCode,
            final String toAccountNum,
            final String toHolderName,
            final Long amount,
            final String idempotencyKey
    ) {
        this.user = user;
        this.fromAccount = fromAccount;
        this.recipient = recipient;
        this.voiceCommand = voiceCommand;
        this.toBankCode = toBankCode;
        this.toAccountNum = toAccountNum;
        this.toHolderName = toHolderName;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = TransferStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    /** FDS 평가를 시작한다. */
    public void startRiskReview() {
        transitionTo(TransferStatus.RISK_REVIEW);
    }

    /** 이체를 완료 처리한다. FDS 평가를 거치지 않았다면 전이가 거부된다. */
    public void complete(final LocalDateTime now) {
        transitionTo(TransferStatus.COMPLETED);
        this.completedAt = now;
    }

    /** 고위험으로 차단한다. */
    public void block(final String reason) {
        transitionTo(TransferStatus.BLOCKED);
        this.failReason = reason;
    }

    /** 외부 연동 실패 등으로 실패 처리한다. */
    public void fail(final String reason) {
        transitionTo(TransferStatus.FAILED);
        this.failReason = reason;
    }

    /** 사용자가 취소한다. */
    public void cancel() {
        transitionTo(TransferStatus.CANCELED);
    }

    private void transitionTo(final TransferStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s -> %s".formatted(this.status, next)
            );
        }
        this.status = next;
    }
}
