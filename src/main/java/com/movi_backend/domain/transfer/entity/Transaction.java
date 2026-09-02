package com.movi_backend.domain.transfer.entity;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
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

/**
 * 거래 내역.
 *
 * <p>{@code category}는 음성 필터용이다. "지난달 식비 얼마 썼어?" 같은 명령을 처리할 때 쓴다.
 */
@Getter
@Entity
@Table(name = "transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * 이 거래를 만든 이체.
     *
     * <p>은행에서 내려받은 입출금처럼 우리 서비스를 거치지 않은 거래는 {@code null}이다.
     * FDS 판정은 이체에 달려 있어, 거래내역에서 위험 표시를 하려면 이 연결이 필요하다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "tran_type", nullable = false, length = 10)
    private TransactionType tranType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    @Column(name = "counterparty_account", length = 255)
    private String counterpartyAccount;

    /** 음성 필터용 분류 (식비/공과금 등) */
    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "tran_datetime", nullable = false)
    private LocalDateTime tranDatetime;

    @Column(name = "memo", length = 200)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TransactionSource source;

    @Builder
    private Transaction(
            final Account account,
            final Transfer transfer,
            final TransactionType tranType,
            final Long amount,
            final Long balanceAfter,
            final String counterpartyName,
            final String counterpartyAccount,
            final String category,
            final LocalDateTime tranDatetime,
            final String memo,
            final TransactionSource source
    ) {
        this.account = account;
        this.transfer = transfer;
        this.tranType = tranType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterpartyName = counterpartyName;
        this.counterpartyAccount = counterpartyAccount;
        this.category = category;
        this.tranDatetime = tranDatetime;
        this.memo = memo;
        this.source = source;
    }

    public void classifyAs(final String category) {
        this.category = category;
    }
}
