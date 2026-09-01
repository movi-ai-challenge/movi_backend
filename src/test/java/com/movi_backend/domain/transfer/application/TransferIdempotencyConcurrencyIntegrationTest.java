package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.application.port.OpenBankingTransferPort;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.fds.client.FdsAssessmentClient;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
class TransferIdempotencyConcurrencyIntegrationTest {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SensitiveDataCrypto sensitiveDataCrypto;

    @MockitoBean
    private BalanceInquiryService balanceInquiryService;

    @MockitoBean
    private FdsAssessmentClient fdsAssessmentClient;

    @MockitoBean
    private OpenBankingTransferPort openBankingTransferPort;

    @MockitoBean
    private TransferRiskAlertPort transferRiskAlertPort;

    @BeforeEach
    void setUpExternalResponses() {
        given(balanceInquiryService.refresh(any(Long.class), any(Account.class)))
                .willAnswer(invocation -> BalanceSnapshot.builder()
                        .account(invocation.getArgument(1))
                        .balanceAmount(1_000_000L)
                        .availableAmount(1_000_000L)
                        .build());
        given(fdsAssessmentClient.assess(any(FdsAssessmentRequest.class)))
                .willAnswer(invocation -> lowRiskResponse(invocation.getArgument(0)));
        given(openBankingTransferPort.transfer(any(), any()))
                .willReturn(OpenBankingTransferResult.of(
                        "test-bank-tran-id",
                        LocalDateTime.now(),
                        950_000L
                ));
    }

    @Test
    @DisplayName("동일 사용자의 같은 멱등성 키 동시 요청은 이체와 외부 호출을 한 번만 수행한다")
    void 동일_사용자의_같은_멱등성_키_동시_요청은_한_번만_수행한다() throws Exception {
        final String idempotencyKey = UUID.randomUUID().toString();
        final ConfirmedTransferCommand command = createCommand("same-user", idempotencyKey);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            final Future<TransferExecutionResult> first = executor.submit(
                    () -> executeTogether(command, ready, start)
            );
            final Future<TransferExecutionResult> second = executor.submit(
                    () -> executeTogether(command, ready, start)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final TransferExecutionResult firstResult = first.get(10, TimeUnit.SECONDS);
            final TransferExecutionResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.transferId()).isEqualTo(secondResult.transferId());
            assertThat(firstResult.status()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(secondResult.status()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(transferRepository.findByIdempotencyKeyAndUserId(
                    idempotencyKey,
                    command.user().getId()
            )).isPresent();
            verify(fdsAssessmentClient, times(1)).assess(any(FdsAssessmentRequest.class));
            verify(openBankingTransferPort, times(1)).transfer(any(), any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("서로 다른 사용자는 같은 멱등성 키로 결과를 공유하지 않는다")
    void 서로_다른_사용자는_같은_멱등성_키로_결과를_공유하지_않는다() {
        final String idempotencyKey = UUID.randomUUID().toString();
        final ConfirmedTransferCommand firstCommand = createCommand("first-user", idempotencyKey);
        final ConfirmedTransferCommand secondCommand = createCommand("second-user", idempotencyKey);

        final TransferExecutionResult firstResult = transferExecutionService.execute(firstCommand);
        final TransferExecutionResult secondResult = transferExecutionService.execute(secondCommand);

        assertThat(firstResult.transferId()).isNotEqualTo(secondResult.transferId());
        assertThat(transferRepository.findByIdempotencyKeyAndUserId(
                idempotencyKey,
                firstCommand.user().getId()
        )).isPresent();
        assertThat(transferRepository.findByIdempotencyKeyAndUserId(
                idempotencyKey,
                secondCommand.user().getId()
        )).isPresent();
        verify(fdsAssessmentClient, times(2)).assess(any(FdsAssessmentRequest.class));
        verify(openBankingTransferPort, times(2)).transfer(any(), any());
    }

    private TransferExecutionResult executeTogether(
            final ConfirmedTransferCommand command,
            final CountDownLatch ready,
            final CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 실행 시작 대기 시간 초과");
        }
        return transferExecutionService.execute(command);
    }

    private ConfirmedTransferCommand createCommand(
            final String fixtureName,
            final String idempotencyKey
    ) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            final User user = User.builder()
                    .name(fixtureName)
                    .phone("encrypted-" + fixtureName)
                    .userType(UserType.SENIOR)
                    .build();
            entityManager.persist(user);

            final OpenbankingConnection connection = OpenbankingConnection.builder()
                    .user(user)
                    .userSeqNo("seq-" + UUID.randomUUID())
                    .accessToken(sensitiveDataCrypto.encrypt("access-token"))
                    .refreshToken(sensitiveDataCrypto.encrypt("refresh-token"))
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .scope("transfer")
                    .build();
            entityManager.persist(connection);

            final Account account = Account.builder()
                    .user(user)
                    .connection(connection)
                    .fintechUseNum("fintech-" + UUID.randomUUID())
                    .bankCode("004")
                    .bankName("국민은행")
                    .accountNumMasked("123-***-456789")
                    .alias("생활비 통장")
                    .accountType(AccountType.DEPOSIT)
                    .build();
            entityManager.persist(account);

            final TransferRecipient recipient = TransferRecipient.builder()
                    .user(user)
                    .nickname("엄마")
                    .bankCode("088")
                    .accountNum(sensitiveDataCrypto.encrypt("110123456789"))
                    .holderName("김영희")
                    .build();
            entityManager.persist(recipient);
            entityManager.flush();

            return ConfirmedTransferCommand.of(
                    user,
                    account,
                    recipient,
                    null,
                    null,
                    50_000L,
                    idempotencyKey,
                    new BigDecimal("0.95")
            );
        });
    }

    private FdsAssessmentResponse lowRiskResponse(final FdsAssessmentRequest request) {
        return FdsAssessmentResponse.of(
                request.requestId(),
                "test-model-v1",
                "test-policy-v1",
                FdsScores.of(
                        new BigDecimal("0.20"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.20")
                ),
                RiskLevel.LOW,
                FdsDecision.ALLOW,
                List.of(),
                5
        );
    }
}
