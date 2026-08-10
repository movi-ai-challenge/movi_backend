package com.movi_backend.domain.account.entity;

import com.movi_backend.domain.account.type.ConnectionStatus;
import com.movi_backend.domain.auth.entity.User;
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
 * 오픈뱅킹 연결. 금결원 사용자일련번호({@code userSeqNo})가 유일하다.
 *
 * <p>만료된 토큰으로 호출하면 오픈뱅킹이 거부하므로, 사용 전 {@link #isExpired(LocalDateTime)}로
 * 확인하고 갱신한다. 토큰은 AES 암호화 대상이다.
 */
@Getter
@Entity
@Table(name = "openbanking_connections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenbankingConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_seq_no", nullable = false, length = 50)
    private String userSeqNo;

    @Column(name = "access_token", nullable = false, length = 1024)
    private String accessToken;

    @Column(name = "refresh_token", length = 1024)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "scope", length = 200)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Builder
    private OpenbankingConnection(
            final User user,
            final String userSeqNo,
            final String accessToken,
            final String refreshToken,
            final LocalDateTime expiresAt,
            final String scope
    ) {
        this.user = user;
        this.userSeqNo = userSeqNo;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
        this.status = ConnectionStatus.ACTIVE;
        this.connectedAt = LocalDateTime.now();
    }

    public boolean isExpired(final LocalDateTime now) {
        return now.isAfter(this.expiresAt);
    }

    public boolean isUsable(final LocalDateTime now) {
        return this.status == ConnectionStatus.ACTIVE && !isExpired(now);
    }

    public void refresh(
            final String accessToken,
            final String refreshToken,
            final LocalDateTime expiresAt
    ) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.status = ConnectionStatus.ACTIVE;
    }

    public void expire() {
        this.status = ConnectionStatus.EXPIRED;
    }

    public void revoke() {
        this.status = ConnectionStatus.REVOKED;
    }
}
