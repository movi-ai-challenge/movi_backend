package com.movi_backend.domain.fds.entity;

import com.movi_backend.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자별 이체 행동 프로필. FDS 피처 소스이며 PK가 곧 사용자 ID다.
 *
 * <p>배치로 갱신한다. <b>프로필이 비어 있으면 이상치 판정이 무의미하므로</b>
 * 이력이 부족한 신규 사용자에게는 별도 정책이 필요하다.
 */
@Getter
@Entity
@Table(name = "user_transfer_profiles")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTransferProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "avg_amount", nullable = false)
    private Long avgAmount;

    @Column(name = "max_amount", nullable = false)
    private Long maxAmount;

    @Column(name = "stddev_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal stddevAmount;

    /** 주 이체 시간대. 예: [9,12,18] */
    @Column(name = "common_hours", columnDefinition = "json")
    private String commonHours;

    @Column(name = "transfer_count_30d", nullable = false)
    private int transferCount30d;

    @Column(name = "distinct_recipients_30d", nullable = false)
    private int distinctRecipients30d;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserTransferProfile(final User user) {
        this.user = user;
        this.avgAmount = 0L;
        this.maxAmount = 0L;
        this.stddevAmount = BigDecimal.ZERO;
        this.transferCount30d = 0;
        this.distinctRecipients30d = 0;
    }

    /** 이력이 없어 이상치 판정을 신뢰할 수 없는 상태인지 여부 */
    public boolean isColdStart() {
        return this.transferCount30d == 0;
    }

    public void refresh(
            final Long avgAmount,
            final Long maxAmount,
            final BigDecimal stddevAmount,
            final String commonHours,
            final int transferCount30d,
            final int distinctRecipients30d
    ) {
        this.avgAmount = avgAmount;
        this.maxAmount = maxAmount;
        this.stddevAmount = stddevAmount;
        this.commonHours = commonHours;
        this.transferCount30d = transferCount30d;
        this.distinctRecipients30d = distinctRecipients30d;
    }
}
