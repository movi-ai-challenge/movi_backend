package com.movi_backend.domain.account.entity;

import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연결 계좌. 핀테크이용번호({@code fintechUseNum})가 계좌의 실질 식별자다.
 *
 * <p>{@code alias}는 음성 별칭이다. "월급통장에서 보내줘" 같은 명령을 해석할 때 쓴다.
 *
 * <p><b>사용자당 {@code primary} 계좌는 최대 1개다.</b> 기본 계좌를 바꿀 때는 기존 것을
 * {@link #releasePrimary()}로 먼저 해제한다.
 */
@Getter
@Entity
@Table(
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_account_user_alias",
                columnNames = {"user_id", "account_alias"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private OpenbankingConnection connection;

    @Column(name = "fintech_use_num", nullable = false, length = 50)
    private String fintechUseNum;

    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "account_num_masked", nullable = false, length = 255)
    private String accountNumMasked;

    /** 음성 별칭 (예: 월급통장) */
    @Column(name = "account_alias", length = 50)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private Account(
            final User user,
            final OpenbankingConnection connection,
            final String fintechUseNum,
            final String bankCode,
            final String bankName,
            final String accountNumMasked,
            final String alias,
            final AccountType accountType
    ) {
        this.user = user;
        this.connection = connection;
        this.fintechUseNum = fintechUseNum;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNumMasked = accountNumMasked;
        this.alias = alias;
        this.accountType = accountType;
        this.primary = false;
        this.active = true;
    }

    public void changeAlias(final String alias) {
        this.alias = alias;
    }

    /**
     * 음성으로 읽어 줄 계좌 이름. 사용자가 붙인 별칭이 있으면 그것을 쓴다 —
     * "국민은행 계좌"보다 "생활비 통장"이 듣는 사람에게 어느 계좌인지 분명하다.
     */
    public String toVoiceName() {
        if (this.alias == null || this.alias.isBlank()) {
            return this.bankName + " 계좌";
        }
        return this.alias;
    }

    public void designateAsPrimary() {
        this.primary = true;
    }

    public void releasePrimary() {
        this.primary = false;
    }

    public void deactivate() {
        this.active = false;
        this.primary = false;
    }
}
