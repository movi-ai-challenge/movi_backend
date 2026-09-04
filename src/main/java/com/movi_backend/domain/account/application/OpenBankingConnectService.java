package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingToken;
import com.movi_backend.domain.account.dto.response.ConnectResultResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.account.type.ConnectionStatus;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오픈뱅킹 계좌 연결 (명세서 1.1, 1.2).
 *
 * <pre>
 * 1. startConnect()  — 인증 URL 발급, state 보관
 * 2. 사용자가 오픈뱅킹에서 본인인증·계좌 연결에 동의
 * 3. completeConnect() — state 대조 → 토큰 교환 → 연결·계좌 저장
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenBankingConnectService {

    private final OpenBankingAuthClient authClient;
    private final OpenBankingClient openBankingClient;
    private final OAuthStateStore stateStore;
    private final UserRepository userRepository;
    private final OpenbankingConnectionRepository connectionRepository;
    private final AccountRepository accountRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /** 사용자를 보낼 오픈뱅킹 인증 URL을 만든다. */
    public String startConnect(final Long userId) {
        final String state = stateStore.issue(userId);
        return authClient.buildAuthorizationUrl(state);
    }

    /**
     * 콜백으로 받은 인가 코드를 처리한다.
     *
     * <p>state 가 유효하지 않으면 즉시 거부한다. 이 검증이 없으면 공격자가 자기 계좌를
     * 피해자 계정에 연결할 수 있다.
     *
     * @return 이번에 등록된 계좌 수와 전체 계좌 수
     */
    @Transactional
    public ConnectResultResponse completeConnect(final String authorizationCode, final String state) {
        final Long userId = stateStore.consume(state);
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_OPENBANKING_STATE);
        }

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final OpenBankingToken token = authClient.exchangeCode(authorizationCode);
        final OpenbankingConnection connection = saveConnection(user, token);

        final List<OpenBankingAccount> accounts =
                openBankingClient.fetchAccounts(token.userSeqNo(), token.accessToken());
        final int registered = registerAccounts(user, connection, accounts);
        final int total = accountRepository
                .findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(userId)
                .size();
        return ConnectResultResponse.of(registered, total);
    }

    /** 이미 연결한 적이 있으면 토큰만 갱신한다. user_seq_no 가 UNIQUE 이기 때문이다. */
    private OpenbankingConnection saveConnection(final User user, final OpenBankingToken token) {
        final String encryptedAccessToken = sensitiveDataCrypto.encrypt(token.accessToken());
        final String encryptedRefreshToken = encryptNullable(token.refreshToken());
        return connectionRepository.findByUserSeqNo(token.userSeqNo())
                .map(existing -> {
                    existing.refresh(
                            encryptedAccessToken,
                            encryptedRefreshToken,
                            token.expiresAt()
                    );
                    return existing;
                })
                .orElseGet(() -> connectionRepository.save(
                        OpenbankingConnection.builder()
                                .user(user)
                                .userSeqNo(token.userSeqNo())
                                .accessToken(encryptedAccessToken)
                                .refreshToken(encryptedRefreshToken)
                                .expiresAt(token.expiresAt())
                                .scope(token.scope())
                                .build()
                ));
    }

    private String encryptNullable(final String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sensitiveDataCrypto.encrypt(token);
    }

    /**
     * 조회한 계좌를 저장한다. 이미 등록된 핀테크이용번호는 건너뛴다.
     *
     * <p>기본 계좌가 하나도 없으면 첫 계좌를 기본으로 지정한다. 계좌를 지정하지 않은
     * 음성 명령이 곧바로 동작하게 하기 위함이다.
     */
    private int registerAccounts(
            final User user,
            final OpenbankingConnection connection,
            final List<OpenBankingAccount> accounts
    ) {
        int registered = 0;
        for (final OpenBankingAccount source : accounts) {
            final java.util.Optional<Account> existing =
                    accountRepository.findByFintechUseNum(source.fintechUseNum());
            if (existing.isPresent()) {
                final Account savedAccount = existing.get();
                if (!Objects.equals(savedAccount.getUser().getId(), user.getId())) {
                    throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_REGISTERED);
                }
                if (!savedAccount.isActive()) {
                    savedAccount.reactivate(
                            connection,
                            source.bankCode(),
                            source.bankName(),
                            source.accountNumMasked(),
                            source.accountType()
                    );
                    registered++;
                }
                continue;
            }
            accountRepository.save(toAccount(user, connection, source));
            registered++;
        }
        designatePrimaryIfAbsent(user.getId());
        log.info("계좌 연결 완료 userId={} 신규 {}건", user.getId(), registered);
        return registered;
    }

    private Account toAccount(
            final User user,
            final OpenbankingConnection connection,
            final OpenBankingAccount source
    ) {
        return Account.builder()
                .user(user)
                .connection(connection)
                .fintechUseNum(source.fintechUseNum())
                .bankCode(source.bankCode())
                .bankName(source.bankName())
                .accountNumMasked(source.accountNumMasked())
                .accountType(source.accountType())
                .build();
    }

    private void designatePrimaryIfAbsent(final Long userId) {
        if (accountRepository.findByUserIdAndPrimaryTrue(userId).isPresent()) {
            return;
        }
        accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(userId).stream()
                .findFirst()
                .ifPresent(Account::designateAsPrimary);
    }

    /** 연결 상태를 확인한다. 만료됐으면 재연결이 필요하다. */
    @Transactional(readOnly = true)
    public boolean isConnected(final Long userId) {
        return connectionRepository
                .findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE)
                .isPresent();
    }
}
