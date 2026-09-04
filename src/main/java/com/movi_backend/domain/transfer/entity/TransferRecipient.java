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
 * 한 사용자가 돈을 보낸(또는 보낼) 상대 계좌.
 *
 * <p>이 엔티티는 <b>두 가지를 겸한다.</b> 섞으면 안 되는 것이라 이름으로 구분한다.
 *
 * <table>
 *   <tr><th></th><th>주소록 항목</th><th>일회성 송금 대상</th></tr>
 *   <tr><td>{@code addressBook}</td><td>{@code true}</td><td>{@code false}</td></tr>
 *   <tr><td>{@code nickname}</td><td>사용자가 지은 호출명 ("엄마")</td><td>{@code null}</td></tr>
 *   <tr><td>목록·음성 이름 조회</td><td>노출된다</td><td>노출되지 않는다</td></tr>
 * </table>
 *
 * <p>일회성 대상은 <b>주소록이 아니라 거래 상대의 신원</b>이다. 등록하지 않은 계좌로 한 번
 * 보낼 때도 {@code transfers}·{@code fds_assessments}가 가리킬 행이 필요해서 만든다. 이런
 * 행에 별칭을 지어 주지 않는다 — 예전에는 {@code "국민은행 6789"}, 겹치면 {@code "(2)"}를
 * 붙여 저장했는데, 사용자가 짓지 않은 이름이 주소록에 쌓여 부를 수도 지울 수도 없었다.
 *
 * <p><b>같은 사용자·같은 은행·같은 전체 계좌번호는 한 행이다.</b> 일회성으로 보낸 뒤 나중에
 * "엄마"로 등록하면 {@link #promoteToAddressBook}으로 <b>같은 행</b>이 주소록 항목이 된다.
 * 새 행을 만들면 {@code transferCount}가 쪼개져 FDS 의 "처음 보내는 상대" 판단이 흐려진다.
 *
 * <p>{@code verifiedAt}은 <b>예금주조회로 계좌를 확인한 시각</b>이다. 이 값이 없는 행으로는
 * 이체하지 않는다. 검증 없이 접두어만 맞춰 저장하던 시절의 행이 남아 있는데, 별칭 모양이나
 * {@code transferCount}로는 그것이 확인된 계좌인지 알 수 없다.
 *
 * <p>{@code transferCount}는 FDS 의 "처음 보내는 상대" 피처로 직접 쓰인다. <b>이체가 성공한
 * 뒤에만</b> {@link #recordTransfer(LocalDateTime)}로 증가한다 — 행을 만들었다는 이유로
 * 기존 거래자가 되지 않는다.
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

    /**
     * 음성 호출명 (예: 엄마). 주소록 항목에만 있다.
     *
     * <p>일회성 송금 대상은 {@code null}이다. {@code (user_id, nickname)} UNIQUE 아래에서
     * MySQL 은 NULL 을 중복으로 보지 않으므로 여러 행이 함께 있을 수 있다.
     */
    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    @Column(name = "account_num", nullable = false, length = 255)
    private String accountNum;

    /**
     * 계좌번호 중복 확인용 HMAC-SHA256. {@code account_num}은 무작위 IV로 암호화돼 같은
     * 계좌라도 매번 다른 암호문이 나와 직접 비교할 수 없다 — {@code users.phone_hash}와
     * 같은 패턴이다. 유일성은 {@code bank_code}와 함께 본다. 계좌번호는 은행 안에서만
     * 유일해서, 은행을 빼면 다른 은행의 같은 번호를 중복으로 막는다.
     */
    @Column(name = "account_num_hash", nullable = false, length = 64)
    private String accountNumHash;

    /** 예금주조회로 확인된 실제 예금주명. 사용자가 부른 이름을 여기에 적지 않는다. */
    @Column(name = "holder_name", nullable = false, length = 50)
    private String holderName;

    /** 사용자가 이름을 지어 주소록에 올린 항목인지. 목록·음성 이름 조회 대상이 된다. */
    @Column(name = "address_book", nullable = false)
    private boolean addressBook;

    /** 예금주조회로 계좌를 확인한 시각. 없으면 이 행으로 이체하지 않는다. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

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
            final String accountNumHash,
            final String holderName,
            final boolean addressBook,
            final LocalDateTime verifiedAt
    ) {
        this.user = user;
        this.nickname = nickname;
        this.bankCode = bankCode;
        this.accountNum = accountNum;
        this.accountNumHash = accountNumHash;
        this.holderName = holderName;
        this.addressBook = addressBook;
        this.verifiedAt = verifiedAt;
        this.transferCount = 0;
    }

    /** 한 번도 보낸 적 없는 상대인지 여부. FDS 피처 */
    public boolean isFirstTime() {
        return this.transferCount == 0;
    }

    /** 예금주조회로 계좌가 확인된 행인지. 확인되지 않았으면 이체하지 않는다. */
    public boolean isVerified() {
        return this.verifiedAt != null;
    }

    /**
     * 확인 복창과 거래내역에 쓸 이름.
     *
     * <p>주소록 항목이면 사용자가 지은 이름을, 일회성 대상이면 확인된 예금주명을 쓴다.
     * 화면을 보지 않는 사용자가 자기가 부른 말로 되들어야 무엇을 확인하는지 안다.
     */
    public String displayName() {
        if (this.nickname == null || this.nickname.isBlank()) {
            return this.holderName;
        }
        return this.nickname;
    }

    public void recordTransfer(final LocalDateTime now) {
        this.transferCount++;
        this.lastTransferredAt = now;
    }

    /**
     * 예금주조회 결과를 반영한다. 재확인한 계좌에도 쓴다.
     *
     * <p>예금주명이 바뀌어 있으면 확인된 값으로 덮는다 — 검증 없이 채워졌던 이름이 그대로
     * 남아 확인 복창에서 읽히면 안 된다.
     */
    public void verify(final String holderName, final LocalDateTime verifiedAt) {
        this.holderName = holderName;
        this.verifiedAt = verifiedAt;
    }

    /**
     * {@code account_num_hash}가 없던 시절에 저장된 행을 채운다.
     *
     * <p>일회성 마이그레이션({@code docs/migrations/20260903_add_recipient_account_num_hash.sql})
     * 전용이다. 계좌는 등록 후 바뀌지 않으므로 평소에는 이 값을 고칠 일이 없다. 모든 환경의
     * 백필이 끝나면 {@code RecipientAccountHashBackfill}과 함께 지운다.
     */
    public void backfillAccountNumHash(final String accountNumHash) {
        this.accountNumHash = accountNumHash;
    }

    /**
     * 일회성 송금 대상이던 행을 주소록 항목으로 올린다.
     *
     * <p>같은 계좌에 새 행을 만들지 않는 이유는 {@code transferCount}와 거래 이력이 그 행에
     * 달려 있어서다. 새로 만들면 여러 번 보낸 상대가 FDS 에 처음 보내는 상대로 간다.
     */
    public void promoteToAddressBook(final String nickname) {
        this.nickname = nickname;
        this.addressBook = true;
    }
}
