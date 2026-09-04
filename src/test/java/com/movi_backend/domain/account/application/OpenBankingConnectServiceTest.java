package com.movi_backend.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingToken;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenBankingConnectServiceTest {

    @Mock private OpenBankingAuthClient authClient;
    @Mock private OpenBankingClient openBankingClient;
    @Mock private OAuthStateStore stateStore;
    @Mock private UserRepository userRepository;
    @Mock private OpenbankingConnectionRepository connectionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private SensitiveDataCrypto sensitiveDataCrypto;
    @Mock private User user;
    @Mock private Account account;
    @Mock private OpenbankingConnection connection;

    @InjectMocks
    private OpenBankingConnectService service;

    @Test
    @DisplayName("오픈뱅킹 연결 토큰은 암호화해 저장하고 계좌 조회에는 원문을 사용한다")
    void 연결_토큰은_암호화해_저장한다() {
        final LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        final OpenBankingToken token = OpenBankingToken.of(
                "plain-access-token",
                "plain-refresh-token",
                "user-seq-no",
                "login inquiry transfer",
                expiresAt
        );
        given(stateStore.consume("state")).willReturn(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(user));
        given(user.getId()).willReturn(3L);
        given(authClient.exchangeCode("authorization-code")).willReturn(token);
        given(sensitiveDataCrypto.encrypt("plain-access-token"))
                .willReturn("encrypted-access-token");
        given(sensitiveDataCrypto.encrypt("plain-refresh-token"))
                .willReturn("encrypted-refresh-token");
        given(connectionRepository.findByUserSeqNo("user-seq-no"))
                .willReturn(Optional.empty());
        given(connectionRepository.save(any(OpenbankingConnection.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(openBankingClient.fetchAccounts("user-seq-no", "plain-access-token"))
                .willReturn(List.of());
        given(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(3L))
                .willReturn(List.of());

        service.completeConnect("authorization-code", "state");

        final ArgumentCaptor<OpenbankingConnection> captor =
                ArgumentCaptor.forClass(OpenbankingConnection.class);
        then(connectionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAccessToken()).isEqualTo("encrypted-access-token");
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("encrypted-refresh-token");
        then(openBankingClient).should().fetchAccounts("user-seq-no", "plain-access-token");
    }

    @Test
    @DisplayName("해제한 본인 계좌를 다시 연결하면 기존 계좌를 활성화한다")
    void 해제한_계좌를_다시_연결하면_재활성화한다() {
        final LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        final OpenBankingToken token = OpenBankingToken.of(
                "plain-access-token",
                "plain-refresh-token",
                "user-seq-no",
                "login inquiry transfer",
                expiresAt
        );
        final OpenBankingAccount source = OpenBankingAccount.of(
                "fintech-use-num",
                "088",
                "신한은행",
                "***1234",
                "사용자",
                AccountType.DEPOSIT
        );
        given(stateStore.consume("state")).willReturn(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(user));
        given(user.getId()).willReturn(3L);
        given(authClient.exchangeCode("authorization-code")).willReturn(token);
        given(sensitiveDataCrypto.encrypt("plain-access-token")).willReturn("encrypted-access");
        given(sensitiveDataCrypto.encrypt("plain-refresh-token")).willReturn("encrypted-refresh");
        given(connectionRepository.findByUserSeqNo("user-seq-no"))
                .willReturn(Optional.of(connection));
        given(openBankingClient.fetchAccounts("user-seq-no", "plain-access-token"))
                .willReturn(List.of(source));
        given(accountRepository.findByFintechUseNum("fintech-use-num"))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(account.isActive()).willReturn(false);
        given(accountRepository.findByUserIdAndPrimaryTrue(3L)).willReturn(Optional.empty());
        given(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(3L))
                .willReturn(List.of(account));

        final var response = service.completeConnect("authorization-code", "state");

        assertThat(response.registeredCount()).isEqualTo(1);
        then(account).should().reactivate(
                connection,
                "088",
                "신한은행",
                "***1234",
                AccountType.DEPOSIT
        );
    }
}
