package com.movi_backend.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 잔액 조회 시점 기록.
 *
 * <p>오픈뱅킹 잔액 조회는 호출 비용·지연이 있어 캐시가 필요하고,
 * FDS 피처(잔액 대비 이체 비율)로도 쓰인다. 조회할 때마다 남긴다.
 */
@Getter
@Entity
@Table(name = "balance_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "balance_amount", nullable = false)
    private Long balanceAmount;

    @Column(name = "available_amount", nullable = false)
    private Long availableAmount;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    private BalanceSnapshot(
            final Account account,
            final Long balanceAmount,
            final Long availableAmount
    ) {
        this.account = account;
        this.balanceAmount = balanceAmount;
        this.availableAmount = availableAmount;
        this.fetchedAt = LocalDateTime.now();
    }
}
