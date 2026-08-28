package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.request.TransferReviewRequest;
import com.movi_backend.domain.transfer.dto.response.TransferResultResponse;
import com.movi_backend.domain.transfer.dto.response.TransferReviewResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectTransferServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ACCOUNT_ID = 12L;
    private static final Long RECIPIENT_ID = 8L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRegistrationService deviceRegistrationService;

    @Mock
    private TransferTargetResolver transferTargetResolver;

    @Mock
    private TransferExecutionService transferExecutionService;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private User user;

    @Mock
    private Account account;

    @Mock
    private TransferRecipient recipient;

    private TransferConfirmationStore transferConfirmationStore;
    private DirectTransferService directTransferService;

    @BeforeEach
    void setUp() {
        final TransferProperties properties =
                new TransferProperties(1L, 1_000_000L, 3_000_000L, 5);
        transferConfirmationStore = new TransferConfirmationStore(properties);
        directTransferService = new DirectTransferService(
                userRepository,
                deviceRegistrationService,
                transferTargetResolver,
                new TransferValidationService(null, properties),
                transferConfirmationStore,
                transferExecutionService,
                sensitiveDataCrypto
        );

        given(account.getId()).willReturn(ACCOUNT_ID);
        given(account.getAlias()).willReturn("생활비 통장");
        given(account.getBankName()).willReturn("국민은행");
        given(recipient.getId()).willReturn(RECIPIENT_ID);
        given(recipient.getNickname()).willReturn("엄마");
        given(recipient.getHolderName()).willReturn("김영희");
        given(recipient.getAccountNum()).willReturn("encrypted");
        given(sensitiveDataCrypto.decrypt("encrypted")).willReturn("110123456789");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(transferTargetResolver.resolveSourceAccount(USER_ID, null)).willReturn(account);
        given(transferTargetResolver.resolveOwnedAccount(USER_ID, ACCOUNT_ID)).willReturn(account);
        given(transferTargetResolver.resolveOwnedRecipient(USER_ID, RECIPIENT_ID))
                .willReturn(recipient);
    }

    @Test
    @DisplayName("검토하면 확인 ID와 마스킹된 수취 계좌를 돌려주고 이체하지 않는다")
    void 검토하면_확인_ID와_마스킹된_수취_계좌를_돌려주고_이체하지_않는다() {
        // when
        final TransferReviewResponse response = review(50_000L);

        // then
        assertThat(response.confirmationId()).isNotBlank();
        assertThat(response.amount()).isEqualTo(50_000L);
        assertThat(response.recipient().maskedAccountNumber()).isEqualTo("***6789");
        assertThat(response.toVoiceMessage())
                .isEqualTo("생활비 통장에서 엄마 님에게 5만원을 보낼까요?");
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("출금 계좌를 지정하지 않으면 기본 계좌로 검토한다")
    void 출금_계좌를_지정하지_않으면_기본_계좌로_검토한다() {
        // when
        review(50_000L);

        // then
        then(transferTargetResolver).should().resolveSourceAccount(USER_ID, null);
    }

    @Test
    @DisplayName("1회 한도를 넘는 금액은 검토 단계에서 막는다")
    void 일회_한도를_넘는_금액은_검토_단계에서_막는다() {
        // when & then
        assertThatThrownBy(() -> review(1_000_001L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("다른 사용자의 수취인은 검토할 수 없다")
    void 다른_사용자의_수취인은_검토할_수_없다() {
        // given
        willThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .given(transferTargetResolver).resolveOwnedRecipient(USER_ID, RECIPIENT_ID);

        // when & then
        assertThatThrownBy(() -> review(50_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("확인한 송금을 실행하면 검토 시점의 금액과 수취인으로 이체한다")
    void 확인한_송금을_실행하면_검토_시점의_금액과_수취인으로_이체한다() {
        // given
        final String confirmationId = review(50_000L).confirmationId();
        final String idempotencyKey = UUID.randomUUID().toString();
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(transferExecutionService.execute(any())).willReturn(completedResult());

        // when
        final TransferResultResponse result = directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, idempotencyKey, null)
        );

        // then
        final ArgumentCaptor<ConfirmedTransferCommand> captor =
                ArgumentCaptor.forClass(ConfirmedTransferCommand.class);
        then(transferExecutionService).should().execute(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(50_000L);
        assertThat(captor.getValue().recipient()).isEqualTo(recipient);
        assertThat(captor.getValue().fromAccount()).isEqualTo(account);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(captor.getValue().voiceCommand()).isNull();
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.toVoiceMessage()).isEqualTo("김영희 님에게 5만원을 보냈어요.");
    }

    @Test
    @DisplayName("같은 확인을 다른 멱등성 키로 실행하면 두 번째 요청은 이체하지 않는다")
    void 같은_확인을_다른_멱등성_키로_실행하면_두_번째_요청은_이체하지_않는다() {
        // given
        final String confirmationId = review(50_000L).confirmationId();
        given(transferExecutionService.findCompletedResult(any(), any()))
                .willReturn(Optional.empty());
        given(transferExecutionService.execute(any())).willReturn(riskReviewResult());
        directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, UUID.randomUUID().toString(), null)
        );

        // when & then — 사용자가 실행 버튼을 두 번 눌러 키가 새로 만들어진 상황
        assertThatThrownBy(() -> directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, UUID.randomUUID().toString(), null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRMATION_INVALID);
        then(transferExecutionService).should().execute(any());
    }

    @Test
    @DisplayName("같은 키로 다시 요청하면 이미 끝난 송금의 결과를 그대로 돌려준다")
    void 같은_키로_다시_요청하면_이미_끝난_송금의_결과를_그대로_돌려준다() {
        // given — 응답을 받지 못한 사용자가 같은 키로 재시도하는 상황
        final String confirmationId = review(50_000L).confirmationId();
        final String idempotencyKey = UUID.randomUUID().toString();
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.of(completedResult()));

        // when
        final TransferResultResponse result = directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, idempotencyKey, null)
        );

        // then
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("확인 ID가 없거나 만료됐으면 이체 실행을 호출하지 않는다")
    void 확인_ID가_없거나_만료됐으면_이체_실행을_호출하지_않는다() {
        // given
        given(transferExecutionService.findCompletedResult(any(), any()))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRMATION_INVALID);
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("다른 사용자의 확인 ID로는 이체할 수 없다")
    void 다른_사용자의_확인_ID로는_이체할_수_없다() {
        // given
        final String confirmationId = review(50_000L).confirmationId();
        given(transferExecutionService.findCompletedResult(any(), any()))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> directTransferService.execute(
                4L,
                new TransferExecuteRequest(confirmationId, UUID.randomUUID().toString(), null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIRMATION_INVALID);
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("멱등성 키가 UUID가 아니면 이체 실행을 호출하지 않는다")
    void 멱등성_키가_UUID가_아니면_이체_실행을_호출하지_않는다() {
        // given
        final String confirmationId = review(50_000L).confirmationId();

        // when & then
        assertThatThrownBy(() -> directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, "not-a-uuid", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("차단된 송금도 결과로 돌려주고 차단 안내를 읽어 준다")
    void 차단된_송금도_결과로_돌려주고_차단_안내를_읽어_준다() {
        // given
        final String confirmationId = review(50_000L).confirmationId();
        given(transferExecutionService.findCompletedResult(any(), any()))
                .willReturn(Optional.empty());
        given(transferExecutionService.execute(any())).willReturn(new TransferExecutionResult(
                101L,
                TransferStatus.BLOCKED,
                RiskLevel.HIGH,
                50_000L,
                "김영희",
                null
        ));

        // when
        final TransferResultResponse result = directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(confirmationId, UUID.randomUUID().toString(), null)
        );

        // then
        assertThat(result.status()).isEqualTo(TransferStatus.BLOCKED);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.toVoiceMessage())
                .isEqualTo(ErrorCode.HIGH_RISK_BLOCKED.getVoiceMessage());
    }

    private TransferReviewResponse review(final long amount) {
        return directTransferService.review(
                USER_ID,
                new TransferReviewRequest(RECIPIENT_ID, amount, null)
        );
    }

    private TransferExecutionResult completedResult() {
        return new TransferExecutionResult(
                101L,
                TransferStatus.COMPLETED,
                RiskLevel.LOW,
                50_000L,
                "김영희",
                LocalDateTime.of(2026, 8, 28, 10, 0)
        );
    }

    private TransferExecutionResult riskReviewResult() {
        return new TransferExecutionResult(
                101L,
                TransferStatus.RISK_REVIEW,
                RiskLevel.LOW,
                50_000L,
                "김영희",
                null
        );
    }

    @Test
    @DisplayName("실행 요청의 기기 식별자를 FDS 평가로 넘긴다")
    void 실행_요청의_기기_식별자를_FDS_평가로_넘긴다() {
        // given — 직접 입력에는 음성 세션이 없어 실행 요청이 기기를 실어 보낸다
        final String confirmationId = review(50_000L).confirmationId();
        final Device device = Device.builder()
                .user(user)
                .deviceUuid("device-uuid-1")
                .build();
        device.trust();
        given(deviceRegistrationService.findOwnedDevice(USER_ID, "device-uuid-1"))
                .willReturn(device);
        given(transferExecutionService.findCompletedResult(any(), any()))
                .willReturn(Optional.empty());
        given(transferExecutionService.execute(any())).willReturn(completedResult());

        // when
        directTransferService.execute(
                USER_ID,
                new TransferExecuteRequest(
                        confirmationId,
                        UUID.randomUUID().toString(),
                        "device-uuid-1"
                )
        );

        // then
        final ArgumentCaptor<ConfirmedTransferCommand> captor =
                ArgumentCaptor.forClass(ConfirmedTransferCommand.class);
        then(transferExecutionService).should().execute(captor.capture());
        assertThat(captor.getValue().device()).isEqualTo(device);
    }
}
