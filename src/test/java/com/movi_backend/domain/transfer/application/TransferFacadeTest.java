package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.fds.application.FdsAssessmentService;
import com.movi_backend.domain.fds.dto.FdsEvaluationCommand;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.FdsDecision;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferResult;
import com.movi_backend.domain.transfer.dto.PreparedTransfer;
import com.movi_backend.domain.transfer.dto.TransferConfirmation;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.response.TransferResponse;
import com.movi_backend.domain.transfer.infrastructure.OpenBankingClient;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferFacadeTest {

    private static final Long USER_ID = 3L;
    private static final Long TRANSFER_ID = 101L;
    private static final Long AMOUNT = 50_000L;
    private static final String IDEMPOTENCY_KEY = "voice-transfer-key-1";
    private static final String FINTECH_USE_NUM = "fintech-use-num";
    private static final long BALANCE = 320_000L;

    @Mock
    private TransferService transferService;

    @Mock
    private FdsAssessmentService fdsAssessmentService;

    @Mock
    private TransferRiskAlertService transferRiskAlertService;

    @Mock
    private OpenBankingClient openBankingClient;

    @InjectMocks
    private TransferFacade transferFacade;

    // ── 일반 흐름 ─────────────────────────────────────────────

    @Test
    @DisplayName("저위험 이체가 완료되면 COMPLETED 응답과 한국어 금액 안내를 돌려준다")
    void LOW_이체가_완료되면_COMPLETED_상태가_된다() {
        // given
        givenPreparedTransfer();
        given(fdsAssessmentService.evaluate(any(FdsEvaluationCommand.class)))
                .willReturn(assessment(RiskLevel.LOW, FdsDecision.ALLOW));
        given(openBankingClient.transfer(any(OpenBankingTransferCommand.class)))
                .willReturn(OpenBankingTransferResult.success("bank-tran-1"));
        given(transferService.complete(eq(TRANSFER_ID), eq(RiskLevel.LOW), any(LocalDateTime.class)))
                .willReturn(completedResponse(RiskLevel.LOW));

        // when
        final TransferResponse response = transferFacade.execute(USER_ID, request());

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.toVoiceMessage()).isEqualTo("김민수 님에게 오만 원을 보냈어요.");
        then(transferRiskAlertService).should(never()).mediumRiskCompleted(any(), any());
    }

    @Test
    @DisplayName("중위험 이체는 완료 처리하고 보호자에게 통보한다")
    void MEDIUM_이체가_완료되면_보호자에게_통보한다() {
        // given
        givenPreparedTransfer();
        given(fdsAssessmentService.evaluate(any(FdsEvaluationCommand.class)))
                .willReturn(assessment(RiskLevel.MEDIUM, FdsDecision.ALLOW_WITH_ALERT));
        given(openBankingClient.transfer(any(OpenBankingTransferCommand.class)))
                .willReturn(OpenBankingTransferResult.success("bank-tran-1"));
        given(transferService.complete(
                eq(TRANSFER_ID), eq(RiskLevel.MEDIUM), any(LocalDateTime.class)))
                .willReturn(completedResponse(RiskLevel.MEDIUM));

        // when
        final TransferResponse response = transferFacade.execute(USER_ID, request());

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        then(transferRiskAlertService).should().mediumRiskCompleted(USER_ID, TRANSFER_ID);
    }

    // ── 고위험 감지 ───────────────────────────────────────────

    @Test
    @DisplayName("고위험 판정이면 오픈뱅킹을 호출하지 않고 확인 대기로 둔다")
    void HIGH_판정이면_오픈뱅킹을_호출하지_않는다() {
        // given
        givenHighRiskDetected();

        // when
        final TransferResponse response = transferFacade.execute(USER_ID, request());

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.HOLD);
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
        then(transferService).should(never())
                .complete(any(), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("고위험이 감지되면 보호자와 본인 모두에게 알린다")
    void 고위험이_감지되면_보호자와_본인에게_알린다() {
        // given
        givenHighRiskDetected();

        // when
        transferFacade.execute(USER_ID, request());

        // then
        then(transferRiskAlertService).should().highRiskDetected(USER_ID, TRANSFER_ID);
    }

    @Test
    @DisplayName("확인 대기 응답은 완료가 아니라 재질문 문구를 읽어 준다")
    void 확인_대기_응답은_재질문_문구를_읽어_준다() {
        // given
        givenHighRiskDetected();

        // when
        final TransferResponse response = transferFacade.execute(USER_ID, request());

        // then
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.toVoiceMessage())
                .contains("정말 보내시겠어요?")
                .contains("오만 원")
                .doesNotContain("보냈어요");
    }

    // ── 본인 재확인 ───────────────────────────────────────────

    @Test
    @DisplayName("본인이 재확인하면 그제서야 오픈뱅킹 이체를 실행한다")
    void 재확인하면_이체를_실행한다() {
        // given
        given(transferService.prepareConfirmation(USER_ID, TRANSFER_ID))
                .willReturn(TransferConfirmation.ready(prepared()));
        given(openBankingClient.transfer(any(OpenBankingTransferCommand.class)))
                .willReturn(OpenBankingTransferResult.success("bank-tran-1"));
        given(transferService.complete(eq(TRANSFER_ID), eq(RiskLevel.HIGH), any(LocalDateTime.class)))
                .willReturn(completedResponse(RiskLevel.HIGH));

        // when
        final TransferResponse response = transferFacade.confirm(USER_ID, TRANSFER_ID);

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        then(openBankingClient).should().transfer(any(OpenBankingTransferCommand.class));
        then(transferRiskAlertService).should().highRiskConfirmed(USER_ID, TRANSFER_ID);
    }

    @Test
    @DisplayName("확인 시간이 지나면 차단으로 확정하고 보호자에게 알린다")
    void 확인_시간이_지나면_차단된다() {
        // given
        given(transferService.prepareConfirmation(USER_ID, TRANSFER_ID))
                .willReturn(TransferConfirmation.expiredConfirmation());

        // when & then
        assertThatThrownBy(() -> transferFacade.confirm(USER_ID, TRANSFER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRANSFER_CONFIRMATION_EXPIRED);
        then(transferRiskAlertService).should().transferBlocked(USER_ID, TRANSFER_ID);
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
    }

    @Test
    @DisplayName("같은 확인 요청이 두 번 들어와도 이체는 한 번만 실행된다")
    void 확인_요청이_중복되어도_이체는_한_번만_실행된다() {
        // given
        given(transferService.prepareConfirmation(USER_ID, TRANSFER_ID))
                .willReturn(TransferConfirmation.alreadyCompleted(completedResponse(RiskLevel.HIGH)));

        // when
        final TransferResponse response = transferFacade.confirm(USER_ID, TRANSFER_ID);

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
        then(transferService).should(never())
                .complete(any(), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("본인이 거절하면 차단하고 보호자에게 알린다")
    void 본인이_거절하면_차단한다() {
        // given
        given(transferService.decline(USER_ID, TRANSFER_ID)).willReturn(blockedResponse());

        // when
        final TransferResponse response = transferFacade.decline(USER_ID, TRANSFER_ID);

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.BLOCKED);
        then(transferRiskAlertService).should().transferBlocked(USER_ID, TRANSFER_ID);
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
    }

    // ── 실패·방어 ─────────────────────────────────────────────

    @Test
    @DisplayName("FDS 평가에 실패하면 이체를 실패로 바꾸고 완료 메시지를 만들지 않는다")
    void FDS_평가가_실패하면_이체하지_않는다() {
        // given
        givenPreparedTransfer();
        given(fdsAssessmentService.evaluate(any(FdsEvaluationCommand.class)))
                .willThrow(new BusinessException(ErrorCode.ASSESSMENT_FAILED));

        // when & then
        assertThatThrownBy(() -> transferFacade.execute(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_FAILED);
        then(transferService).should().fail(eq(TRANSFER_ID), anyString());
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
    }

    @Test
    @DisplayName("오픈뱅킹 이체가 실패하면 완료 처리하지 않는다")
    void 오픈뱅킹_이체가_실패하면_완료하지_않는다() {
        // given
        givenPreparedTransfer();
        given(fdsAssessmentService.evaluate(any(FdsEvaluationCommand.class)))
                .willReturn(assessment(RiskLevel.LOW, FdsDecision.ALLOW));
        given(openBankingClient.transfer(any(OpenBankingTransferCommand.class)))
                .willReturn(OpenBankingTransferResult.failure());

        // when & then
        assertThatThrownBy(() -> transferFacade.execute(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TRANSFER_EXECUTION_FAILED);
        then(transferService).should(never())
                .complete(any(), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("잔액이 부족하면 평가도 이체도 하지 않는다")
    void 잔액이_부족하면_이체하지_않는다() {
        // given
        given(transferService.findProcessed(IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(transferService.create(eq(USER_ID), any(TransferCreateCommand.class)))
                .willReturn(prepared());
        given(openBankingClient.inquireBalance(FINTECH_USE_NUM)).willReturn(1_000L);

        // when & then
        assertThatThrownBy(() -> transferFacade.execute(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        then(fdsAssessmentService).should(never()).evaluate(any(FdsEvaluationCommand.class));
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
    }

    @Test
    @DisplayName("같은 멱등성 키로 재요청해도 실제 이체는 한 번만 수행된다")
    void 같은_멱등성_키로_재요청해도_이체는_한_번만_수행된다() {
        // given
        given(transferService.findProcessed(IDEMPOTENCY_KEY))
                .willReturn(Optional.of(completedResponse(RiskLevel.LOW)));

        // when
        final TransferResponse response = transferFacade.execute(USER_ID, request());

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        then(transferService).should(never()).create(any(), any(TransferCreateCommand.class));
        then(openBankingClient).should(never()).transfer(any(OpenBankingTransferCommand.class));
        then(fdsAssessmentService).should(never()).evaluate(any(FdsEvaluationCommand.class));
    }

    // ── 픽스처 ────────────────────────────────────────────────

    private void givenPreparedTransfer() {
        given(transferService.findProcessed(IDEMPOTENCY_KEY)).willReturn(Optional.empty());
        given(transferService.create(eq(USER_ID), any(TransferCreateCommand.class)))
                .willReturn(prepared());
        given(openBankingClient.inquireBalance(FINTECH_USE_NUM)).willReturn(BALANCE);
    }

    private void givenHighRiskDetected() {
        givenPreparedTransfer();
        given(fdsAssessmentService.evaluate(any(FdsEvaluationCommand.class)))
                .willReturn(assessment(RiskLevel.HIGH, FdsDecision.BLOCK));
        given(transferService.hold(TRANSFER_ID, RiskLevel.HIGH)).willReturn(heldResponse());
    }

    private PreparedTransfer prepared() {
        return new PreparedTransfer(
                TRANSFER_ID,
                USER_ID,
                AMOUNT,
                LocalDateTime.now(),
                FINTECH_USE_NUM,
                "004",
                "1234567890",
                "김민수",
                0
        );
    }

    private TransferExecuteRequest request() {
        return new TransferExecuteRequest(
                IDEMPOTENCY_KEY, 1L, null, "004", "1234567890", "김민수", AMOUNT, true, 0.93d);
    }

    private FdsAssessment assessment(final RiskLevel riskLevel, final FdsDecision decision) {
        return FdsAssessment.builder()
                .modelVersion("mock-isolation-forest-v0")
                .anomalyScore(new BigDecimal("0.42"))
                .riskLevel(riskLevel)
                .decision(decision)
                .latencyMs(5)
                .build();
    }

    private TransferResponse completedResponse(final RiskLevel riskLevel) {
        return new TransferResponse(
                TRANSFER_ID, TransferStatus.COMPLETED, AMOUNT, "김민수", riskLevel, LocalDateTime.now());
    }

    private TransferResponse heldResponse() {
        return new TransferResponse(
                TRANSFER_ID, TransferStatus.HOLD, AMOUNT, "김민수", RiskLevel.HIGH, null);
    }

    private TransferResponse blockedResponse() {
        return new TransferResponse(
                TRANSFER_ID, TransferStatus.BLOCKED, AMOUNT, "김민수", RiskLevel.HIGH, null);
    }
}
