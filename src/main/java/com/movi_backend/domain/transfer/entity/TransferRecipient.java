package com.movi_backend.domain.transfer.entity;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.global.entity.BaseCreatedEntity;
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
 * 자주 쓰는 수취인.
 *
 * <p>{@code nickname}이 음성 호출명이다. "엄마한테 5만원 보내줘"를 해석하려면 별칭↔계좌 매핑이
 * 필요하다. 사용자당 별칭은 유일하므로 "엄마"가 두 명일 수 없다.
 *
 * <p>{@code transferCount}는 FDS의 "처음 보내는 상대" 피처로 직접 쓰인다.
 * 이체 성공 시 {@link #recordTransfer(LocalDateTime)}로 증가시킨다.
 */
@Getter
@Entity
@Table(name = "transfer_recipients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferRecipient extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 음성 호출명 (예: 엄마) */
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    @Column(name = "account_num", nullable = false, length = 255)
    private String accountNum;

    @Column(name = "holder_name", nullable = false, length = 50)
    private String holderName;

    @Column(name = "transfer_count", nullable = false)
    private int transferCount;

    @Column(name = "last_transferred_at")
    private LocalDateTime lastTransferredAt;

    @Builder
    private TransferRecipient(
            final User user,
            final String nickname,
            final String bankCode,
            final String accountNum,
            final String holderName
    ) {
        this.user = user;
        this.nickname = nickname;
        this.bankCode = bankCode;
        this.accountNum = accountNum;
        this.holderName = holderName;
        this.transferCount = 0;
    }

    /** 한 번도 보낸 적 없는 상대인지 여부. FDS 피처 */
    public boolean isFirstTime() {
        return this.transferCount == 0;
    }

    public void recordTransfer(final LocalDateTime now) {
        this.transferCount++;
        this.lastTransferredAt = now;
    }

    public void changeNickname(final String nickname) {
        this.nickname = nickname;
    }
}
