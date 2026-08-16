package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.client.FdsAssessmentClient;
import com.movi_backend.domain.fds.client.FdsAssessmentResponseValidator;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.domain.fds.client.dto.FdsScores;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.port.TransferExecutionPort;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.entity.VoiceCommand;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TransferExecutionServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ACCOUNT_ID = 12L;
    private static final Long TRANSFER_ID = 101L;

    @Mock private TransferRepository transferRepository;
    @Mock private BalanceSnapshotRepository balanceSnapshotRepository;
    @Mock private UserTransferProfileRepository userTransferProfileRepository;
    @Mock private FdsAssessmentRepository fdsAssessmentRepository;
    @Mock private FdsAssessmentClient fdsAssessmentClient;
    @Mock private TransferExecutionPort transferExecutionPort;
    @Mock private TransferRiskAlertPort transferRiskAlertPort;
    @Mock private User user;
    @Mock private Account account;
    @Mock private OpenbankingConnection connection;
    @Mock private TransferRecipient recipient;
    @Mock private VoiceCommand voiceCommand;
    @Mock private Device device;
    @Mock private BalanceSnapshot balanceSnapshot;

    @Test
    @DisplayName("저위험 이체를 확인하면 FDS 평가 후 이체를 완료한다")
    void 저위험_이체를_확인하면_FDS_평가_후_이체를_완료한다() {
        // given
        final ConfirmedTransferCommand command = givenCommand(50_000L);
        givenFdsResponse(RiskLevel.LOW);
        givenAssessmentSaved();
        final TransferExecutionService service = createService();

        // when
        final TransferExecutionResult result = service.execute(command);

        // then
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.completedAt()).isNotNull();
        then(transferExecutionPort).should().execute(any(Transfer.class));
        then(transferRiskAlertPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("중위험 이체를 확인하면 이체를 완료하고 보호자 알림을 요청한다")
    void 중위험_이체를_확인하면_이체를_완료하고_보호자_알림을_요청한다() {
        // given
        final ConfirmedTransferCommand command = givenCommand(150_000L);
        givenFdsResponse(RiskLevel.MEDIUM);
        givenAssessmentSaved();
        final TransferExecutionService service = createService();

        // when
        final TransferExecutionResult result = service.execute(command);

        // then
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        then(transferExecutionPort).should().execute(any(Transfer.class));
        then(transferRiskAlertPort).should().send(any(Transfer.class), any(FdsAssessment.class));
    }

    @Test
    @DisplayName("고위험 이체를 확인하면 이체를 차단하고 실행 포트를 호출하지 않는다")
    void 고위험_이체를_확인하면_이체를_차단하고_실행_포트를_호출하지_않는다() {
        // given
        final ConfirmedTransferCommand command = givenCommand(800_000L);
        givenFdsResponse(RiskLevel.HIGH);
        givenAssessmentSaved();
        final TransferExecutionService service = createService();

        // when
        final TransferExecutionResult result = service.execute(command);

        // then
        assertThat(result.status()).isEqualTo(TransferStatus.BLOCKED);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        then(transferExecutionPort).shouldHaveNoInteractions();
        then(transferRiskAlertPort).should().send(any(Transfer.class), any(FdsAssessment.class));
    }

    @Test
    @DisplayName("FDS 평가가 실패하면 이체 실행 포트를 호출하지 않는다")
    void FDS_평가가_실패하면_이체_실행_포트를_호출하지_않는다() {
        // given
        final ConfirmedTransferCommand command = givenCommand(50_000L);
        given(fdsAssessmentClient.assess(any(FdsAssessmentRequest.class)))
                .willThrow(new BusinessException(ErrorCode.ASSESSMENT_FAILED));
        final TransferExecutionService service = createService();

        // when & then
        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
        then(transferExecutionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("같은 멱등성 키의 완료 이체를 다시 요청하면 기존 결과를 반환한다")
    void 같은_멱등성_키의_완료_이체를_다시_요청하면_기존_결과를_반환한다() {
        // given
        final String idempotencyKey = UUID.randomUUID().toString();
        given(user.getId()).willReturn(USER_ID);
        final ConfirmedTransferCommand command = ConfirmedTransferCommand.of(
                user,
                account,
                recipient,
                voiceCommand,
                device,
                50_000L,
                idempotencyKey,
                new BigDecimal("0.95")
        );
        final Transfer transfer = Transfer.builder()
                .user(user)
                .fromAccount(account)
                .recipient(recipient)
                .voiceCommand(voiceCommand)
                .toBankCode("088")
                .toAccountNum("encrypted-account")
                .toHolderName("김영희")
                .amount(50_000L)
                .idempotencyKey(command.idempotencyKey())
                .build();
        ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);
        transfer.startRiskReview();
        transfer.complete(java.time.LocalDateTime.now());
        final FdsAssessment assessment = assessmentOf(transfer, RiskLevel.LOW);
        given(transferRepository.findByIdempotencyKeyAndUserId(
                command.idempotencyKey(), USER_ID
        )).willReturn(Optional.of(transfer));
        given(fdsAssessmentRepository.findByTransferId(TRANSFER_ID))
                .willReturn(Optional.of(assessment));
        final TransferExecutionService service = createService();

        // when
        final TransferExecutionResult result = service.execute(command);

        // then
        assertThat(result.transferId()).isEqualTo(TRANSFER_ID);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        then(fdsAssessmentClient).shouldHaveNoInteractions();
        then(transferExecutionPort).shouldHaveNoInteractions();
    }

    private ConfirmedTransferCommand givenCommand(final long amount) {
        final String idempotencyKey = UUID.randomUUID().toString();
        given(user.getId()).willReturn(USER_ID);
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(account.getConnection()).willReturn(connection);
        given(connection.isUsable(any())).willReturn(true);
        given(recipient.getBankCode()).willReturn("088");
        given(recipient.getAccountNum()).willReturn("encrypted-account");
        given(recipient.getHolderName()).willReturn("김영희");
        given(recipient.getTransferCount()).willReturn(1);
        given(recipient.isFirstTime()).willReturn(false);
        given(device.isTrusted()).willReturn(true);
        given(balanceSnapshot.getAvailableAmount()).willReturn(1_000_000L);
        given(transferRepository.findByIdempotencyKeyAndUserId(idempotencyKey, USER_ID))
                .willReturn(Optional.empty());
        given(balanceSnapshotRepository.findTopByAccountIdOrderByFetchedAtDesc(ACCOUNT_ID))
                .willReturn(Optional.of(balanceSnapshot));
        given(userTransferProfileRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(transferRepository.saveAndFlush(any(Transfer.class))).willAnswer(invocation -> {
            final Transfer transfer = invocation.getArgument(0);
            ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);
            return transfer;
        });
        return ConfirmedTransferCommand.of(
                user,
                account,
                recipient,
                voiceCommand,
                device,
                amount,
                idempotencyKey,
                new BigDecimal("0.95")
        );
    }

    private void givenAssessmentSaved() {
        given(fdsAssessmentRepository.save(any(FdsAssessment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenFdsResponse(final RiskLevel riskLevel) {
        given(fdsAssessmentClient.assess(any(FdsAssessmentRequest.class)))
                .willAnswer(invocation -> {
                    final FdsAssessmentRequest request = invocation.getArgument(0);
                    return responseOf(request, riskLevel);
                });
    }

    private FdsAssessmentResponse responseOf(
            final FdsAssessmentRequest request,
            final RiskLevel riskLevel
    ) {
        return FdsAssessmentResponse.of(
                request.requestId(),
                "test-model-v1",
                "test-policy-v1",
                FdsScores.of(
                        new BigDecimal("0.20"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.20")
                ),
                riskLevel,
                FdsDecision.from(riskLevel),
                List.of(),
                5
        );
    }

    private FdsAssessment assessmentOf(
            final Transfer transfer,
            final RiskLevel riskLevel
    ) {
        return FdsAssessment.builder()
                .transfer(transfer)
                .user(user)
                .modelVersion("test-model-v1")
                .anomalyScore(new BigDecimal("0.20"))
                .riskLevel(riskLevel)
                .decision(FdsDecision.from(riskLevel))
                .features("{}")
                .latencyMs(5)
                .build();
    }

    private TransferExecutionService createService() {
        return new TransferExecutionService(
                transferRepository,
                balanceSnapshotRepository,
                userTransferProfileRepository,
                fdsAssessmentRepository,
                fdsAssessmentClient,
                new FdsAssessmentResponseValidator(),
                transferExecutionPort,
                transferRiskAlertPort,
                new ObjectMapper()
        );
    }
}
