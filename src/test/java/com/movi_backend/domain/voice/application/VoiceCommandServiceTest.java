package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.TransactionQueryService;
import com.movi_backend.domain.transfer.application.TransferValidationService;
import com.movi_backend.domain.transfer.application.TransferExecutionService;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.application.BankDirectory;
import com.movi_backend.domain.transfer.application.SpokenAccountNumberParser;
import com.movi_backend.domain.transfer.application.TransferTargetResolver;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.voice.application.model.PendingTransferSlots;
import com.movi_backend.domain.voice.client.VoiceAnalysisClient;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntities;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.entity.VoiceCommand;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceCommandRepository;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceChannel;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.domain.voice.type.VoiceSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VoiceCommandServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long SESSION_ID = 15L;
    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.95");

    @Mock
    private VoiceSessionRepository voiceSessionRepository;

    @Mock
    private VoiceCommandRepository voiceCommandRepository;

    @Mock
    private VoiceAnalysisClient voiceAnalysisClient;

    @Mock
    private TransferValidationService transferValidationService;

    @Mock
    private TransferExecutionService transferExecutionService;

    @Mock
    private TransactionQueryService transactionQueryService;

    @Mock
    private BalanceInquiryService balanceInquiryService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private AudioDurationValidator audioDurationValidator;

    @Mock
    private Account account;

    @Mock
    private TransferRecipient recipient;

    @Test
    @DisplayName("금액이 누락된 이체 음성을 처리하면 슬롯을 저장하고 금액을 재질문한다")
    void 금액이_누락된_이체_음성을_처리하면_슬롯을_저장하고_금액을_재질문한다() {
        // given
        final VoiceSession session = createSession();
        final VoiceAnalysisResponse analysis = VoiceAnalysisResponse.of(
                "voice-123",
                SESSION_ID,
                "엄마 계좌 110-123-123456으로 보내줘",
                HIGH_CONFIDENCE,
                VoiceIntent.TRANSFER,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(null, "엄마", null),
                VoiceEntityConfidences.transfer(null, HIGH_CONFIDENCE, null),
                List.of(VoiceSlot.AMOUNT),
                120
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class))).willReturn(analysis);
        given(transferValidationService.validate(any(), any()))
                .willReturn(TransferClarification.of(
                        List.of(TransferSlot.AMOUNT),
                        "얼마를 보내시겠어요?"
                ));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio()
        );

        // then
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.CLARIFYING);
        assertThat(response.transcript()).isEqualTo("엄마 계좌 ***3456으로 보내줘");
        assertThat(response.transcript()).doesNotContain("110-123-123456");
        assertThat(response.missingSlots()).containsExactly(TransferSlot.AMOUNT);
        assertThat(response.toVoiceMessage()).isEqualTo("얼마를 보내시겠어요?");
        assertThat(session.getPendingSlots()).contains("엄마");
        assertThat(session.getPendingSlots()).contains("\"amount\":null");

        final ArgumentCaptor<VoiceCommand> commandCaptor =
                ArgumentCaptor.forClass(VoiceCommand.class);
        then(voiceCommandRepository).should().save(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getStatus().name()).isEqualTo("CLARIFY");
        assertThat(commandCaptor.getValue().getProcessingMs()).isEqualTo(120);
        assertThat(commandCaptor.getValue().getSttText())
                .isEqualTo("엄마 계좌 ***3456으로 보내줘");
        assertThat(commandCaptor.getValue().getSttText()).doesNotContain("110-123-123456");
    }

    @Test
    @DisplayName("금액 후속 발화를 처리하면 기존 수취인과 병합해 확인을 요청한다")
    void 금액_후속_발화를_처리하면_기존_수취인과_병합해_확인을_요청한다()
            throws Exception {
        // given
        final ObjectMapper objectMapper = new ObjectMapper();
        final VoiceSession session = createSession();
        session.clarify(
                VoiceIntent.TRANSFER,
                objectMapper.writeValueAsString(
                        PendingTransferSlots.clarifying(null, "엄마", null)
                ),
                LocalDateTime.now()
        );
        final VoiceAnalysisResponse analysis = VoiceAnalysisResponse.of(
                "voice-124",
                SESSION_ID,
                "오만 원",
                HIGH_CONFIDENCE,
                VoiceIntent.TRANSFER,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(50_000L, null, null),
                VoiceEntityConfidences.transfer(HIGH_CONFIDENCE, null, null),
                List.of(VoiceSlot.RECIPIENT),
                90
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class))).willReturn(analysis);
        given(transferValidationService.validate(any(), any()))
                .willReturn(ValidatedTransferCommand.of(50_000L, recipient, null));
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.isActive()).willReturn(true);
        given(account.getId()).willReturn(12L);
        given(account.getAlias()).willReturn("생활비 통장");
        given(account.getBankName()).willReturn("국민은행");
        given(recipient.getId()).willReturn(8L);
        given(recipient.getNickname()).willReturn("엄마");
        given(recipient.getHolderName()).willReturn("김영희");
        given(recipient.getBankCode()).willReturn("088");
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = new VoiceCommandService(
                voiceSessionRepository,
                new VoiceSessionExpirationService(voiceSessionRepository),
                voiceCommandRepository,
                voiceAnalysisClient,
                transferValidationService,
                transferExecutionService,
                transactionQueryService,
                balanceInquiryService,
                new TransferTargetResolver(accountRepository, transferRecipientRepository),
                new SpokenAccountNumberParser(),
                new BankDirectory(),
                objectMapper,
                audioDurationValidator
        );

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio()
        );

        // then
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
        assertThat(response.transcript()).isEqualTo("오만 원");
        assertThat(response.amount()).isEqualTo(50_000L);
        assertThat(response.confirmationId()).isNotBlank();
        assertThat(response.toVoiceMessage())
                .isEqualTo("생활비 통장에서 김영희 님에게 5만원을 보낼까요?");
        assertThat(session.getPendingSlots()).contains("\"recipientNickname\":\"엄마\"");

        final ArgumentCaptor<VoiceAnalysisRequest> analysisRequestCaptor =
                ArgumentCaptor.forClass(VoiceAnalysisRequest.class);
        then(voiceAnalysisClient).should().analyze(analysisRequestCaptor.capture());
        assertThat(analysisRequestCaptor.getValue().expectedIntent())
                .isEqualTo(VoiceIntent.TRANSFER);
        assertThat(analysisRequestCaptor.getValue().expectedSlots())
                .containsExactly(VoiceSlot.AMOUNT);

        final ArgumentCaptor<TransferCommandRequest> commandRequestCaptor =
                ArgumentCaptor.forClass(TransferCommandRequest.class);
        then(transferValidationService).should()
                .validate(org.mockito.ArgumentMatchers.eq(USER_ID), commandRequestCaptor.capture());
        assertThat(commandRequestCaptor.getValue().amount()).isEqualTo(50_000L);
        assertThat(commandRequestCaptor.getValue().recipient()).isEqualTo("엄마");
        assertThat(commandRequestCaptor.getValue().recipientConfidence())
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("확인 대기 중 취소 발화를 처리하면 슬롯을 폐기하고 세션을 취소한다")
    void 확인_대기_중_취소_발화를_처리하면_슬롯을_폐기하고_세션을_취소한다()
            throws Exception {
        // given
        final ObjectMapper objectMapper = new ObjectMapper();
        final VoiceSession session = createSession();
        session.awaitConfirmation(
                VoiceIntent.TRANSFER,
                objectMapper.writeValueAsString(PendingTransferSlots.awaitingConfirmation(
                        50_000L,
                        "엄마",
                        null,
                        8L,
                        12L,
                        "confirmation-123"
                )),
                LocalDateTime.now()
        );
        final VoiceAnalysisResponse analysis = VoiceAnalysisResponse.of(
                "voice-125",
                SESSION_ID,
                "아니 취소할게",
                HIGH_CONFIDENCE,
                VoiceIntent.CANCEL,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(null, null, null),
                VoiceEntityConfidences.transfer(null, null, null),
                List.of(),
                80
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class))).willReturn(analysis);
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio()
        );

        // then
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.CANCELED);
        assertThat(response.intent()).isEqualTo(VoiceIntent.CANCEL);
        assertThat(response.transcript()).isEqualTo("아니 취소할게");
        assertThat(response.toVoiceMessage()).isEqualTo("송금을 취소했어요.");
        assertThat(session.getPendingSlots()).isNull();
        assertThat(session.getPendingIntent()).isNull();
        assertThat(session.getEndedAt()).isNotNull();
        then(transferValidationService).shouldHaveNoInteractions();

        final ArgumentCaptor<VoiceCommand> commandCaptor =
                ArgumentCaptor.forClass(VoiceCommand.class);
        then(voiceCommandRepository).should().save(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getIntent()).isEqualTo(VoiceIntent.CANCEL);
        assertThat(commandCaptor.getValue().getResponseText()).isEqualTo("송금을 취소했어요.");
    }

    @Test
    @DisplayName("확인 대기 중 확인 발화를 처리하면 멱등성 키로 이체를 실행하고 세션을 완료한다")
    void 확인_대기_중_확인_발화를_처리하면_멱등성_키로_이체를_실행하고_세션을_완료한다()
            throws Exception {
        // given
        final ObjectMapper objectMapper = new ObjectMapper();
        final VoiceSession session = createSession();
        session.awaitConfirmation(
                VoiceIntent.TRANSFER,
                objectMapper.writeValueAsString(PendingTransferSlots.awaitingConfirmation(
                        50_000L,
                        "엄마",
                        null,
                        8L,
                        12L,
                        "confirmation-123"
                )),
                LocalDateTime.now()
        );
        final VoiceAnalysisResponse analysis = VoiceAnalysisResponse.of(
                "voice-126",
                SESSION_ID,
                "응 보내줘",
                HIGH_CONFIDENCE,
                VoiceIntent.CONFIRM,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(null, null, null),
                VoiceEntityConfidences.transfer(null, null, null),
                List.of(),
                75
        );
        final String idempotencyKey = UUID.randomUUID().toString();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class))).willReturn(analysis);
        given(accountRepository.findById(12L)).willReturn(Optional.of(account));
        given(account.getUser()).willReturn(session.getUser());
        given(account.isActive()).willReturn(true);
        given(transferRecipientRepository.findById(8L)).willReturn(Optional.of(recipient));
        given(recipient.getUser()).willReturn(session.getUser());
        given(voiceCommandRepository.saveAndFlush(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(transferExecutionService.execute(any(ConfirmedTransferCommand.class)))
                .willReturn(new TransferExecutionResult(
                        101L,
                        TransferStatus.COMPLETED,
                        RiskLevel.LOW,
                        50_000L,
                        "김영희",
                        LocalDateTime.now(),
                        List.of()
                ));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio(),
                "confirmation-123",
                idempotencyKey
        );

        // then
        assertThat(response.transferId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("응 보내줘");
        assertThat(response.toVoiceMessage()).isEqualTo("김영희 님에게 5만원을 보냈어요.");
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.COMPLETED);
        assertThat(session.getPendingSlots()).isNull();
        then(transferExecutionService).should().execute(any(ConfirmedTransferCommand.class));
    }

    @Test
    @DisplayName("확인 발화에 확인 ID가 없으면 이체를 실행하지 않는다")
    void 확인_발화에_확인_ID가_없으면_이체를_실행하지_않는다() throws Exception {
        // given
        final VoiceSession session = createAwaitingConfirmationSession();
        final String idempotencyKey = UUID.randomUUID().toString();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createConfirmationAnalysis());
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(
                USER_ID,
                SESSION_ID,
                createAudio(),
                null,
                idempotencyKey
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONFIRMATION_ID);
        then(transferExecutionService).should(never()).execute(any());
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("서버 세션과 다른 확인 ID를 보내면 이체를 실행하지 않는다")
    void 서버_세션과_다른_확인_ID를_보내면_이체를_실행하지_않는다() throws Exception {
        // given
        final VoiceSession session = createAwaitingConfirmationSession();
        final String idempotencyKey = UUID.randomUUID().toString();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.empty());
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createConfirmationAnalysis());
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(
                USER_ID,
                SESSION_ID,
                createAudio(),
                "different-confirmation-id",
                idempotencyKey
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONFIRMATION_ID);
        then(transferExecutionService).should(never()).execute(any());
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("완료된 멱등 요청은 확인 ID 없이도 저장된 이체 결과를 반환한다")
    void 완료된_멱등_요청은_확인_ID_없이도_저장된_이체_결과를_반환한다() {
        // given
        final VoiceSession session = createSession();
        final String idempotencyKey = UUID.randomUUID().toString();
        final TransferExecutionResult completedResult = new TransferExecutionResult(
                101L,
                TransferStatus.COMPLETED,
                RiskLevel.LOW,
                50_000L,
                "김영희",
                LocalDateTime.now(),
                List.of()
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(transferExecutionService.findCompletedResult(USER_ID, idempotencyKey))
                .willReturn(Optional.of(completedResult));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio(),
                null,
                idempotencyKey
        );

        // then
        assertThat(response.transferId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.transcript()).isNull();
        then(voiceAnalysisClient).shouldHaveNoInteractions();
        then(transferExecutionService).should(never()).execute(any());
    }

    @Test
    @DisplayName("다른 사용자의 세션에 명령을 보내면 접근 권한 예외가 발생한다")
    void 다른_사용자의_세션에_명령을_보내면_접근_권한_예외가_발생한다() {
        // given
        final VoiceSession otherUserSession = createSession(99L);
        given(voiceSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(otherUserSession));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(voiceAnalysisClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("지원하지 않는 음성 형식을 보내면 미디어 형식 예외가 발생한다")
    void 지원하지_않는_음성_형식을_보내면_미디어_형식_예외가_발생한다() {
        // given
        final MockMultipartFile invalidAudio = new MockMultipartFile(
                "audio",
                "voice.mp3",
                "audio/mpeg",
                new byte[]{1, 2, 3}
        );
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, invalidAudio))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        then(voiceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("코덱 정보가 포함된 MP4 음성 형식은 정규화해 길이를 검증한다")
    void MP4_음성_형식은_정규화해_길이를_검증한다() {
        // given
        final VoiceSession otherUserSession = createSession(99L);
        final MockMultipartFile mp4Audio = new MockMultipartFile(
                "audio",
                "voice.mp4",
                "audio/mp4;codecs=mp4a.40.2",
                new byte[]{1, 2, 3}
        );
        given(voiceSessionRepository.findById(SESSION_ID))
                .willReturn(Optional.of(otherUserSession));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, mp4Audio))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(audioDurationValidator).should().validate(mp4Audio, "audio/mp4");
        then(voiceAnalysisClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 슬롯이 있는 세션에 발화하면 슬롯을 폐기하고 만료 예외가 발생한다")
    void 만료된_슬롯이_있는_세션에_발화하면_슬롯을_폐기하고_만료_예외가_발생한다()
            throws Exception {
        // given
        final ObjectMapper objectMapper = new ObjectMapper();
        final VoiceSession session = createSession();
        session.clarify(
                VoiceIntent.TRANSFER,
                objectMapper.writeValueAsString(
                        PendingTransferSlots.clarifying(null, "엄마", null)
                ),
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(session, "expiresAt", LocalDateTime.now().minusSeconds(1));
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SLOT_EXPIRED);
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.EXPIRED);
        assertThat(session.getPendingSlots()).isNull();
        then(voiceAnalysisClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재질문 횟수를 초과한 세션에 발화하면 슬롯을 폐기하고 세션을 만료한다")
    void 재질문_횟수를_초과한_세션에_발화하면_슬롯을_폐기하고_세션을_만료한다()
            throws Exception {
        // given
        final ObjectMapper objectMapper = new ObjectMapper();
        final VoiceSession session = createSession();
        final String pendingSlots = objectMapper.writeValueAsString(
                PendingTransferSlots.clarifying(null, "엄마", null)
        );
        session.clarify(VoiceIntent.TRANSFER, pendingSlots, LocalDateTime.now());
        session.clarify(VoiceIntent.TRANSFER, pendingSlots, LocalDateTime.now());
        session.clarify(VoiceIntent.TRANSFER, pendingSlots, LocalDateTime.now());
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RETRY_LIMIT_EXCEEDED);
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.EXPIRED);
        assertThat(session.getPendingSlots()).isNull();
        then(voiceAnalysisClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("거래내역 음성 명령을 처리하면 기본 계좌 내역을 조회해 음성으로 안내한다")
    void 거래내역_음성_명령을_처리하면_기본_계좌_내역을_조회해_음성으로_안내한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createHistoryAnalysis(null, null));
        given(account.getId()).willReturn(12L);
        given(account.isActive()).willReturn(true);
        given(account.toVoiceName()).willReturn("생활비 통장");
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(transactionQueryService.findAll(
                eq(USER_ID), eq(12L), any(), any(), eq(null), eq(0), anyInt()
        )).willReturn(PageResponse.of(
                List.of(
                        createTransaction(1L, TransactionType.OUT, 50000L, "엄마", 24),
                        createTransaction(2L, TransactionType.IN, 3000000L, "회사", 20)
                ),
                0,
                5,
                2L
        ));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(response.intent()).isEqualTo(VoiceIntent.HISTORY);
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.ACTIVE);
        assertThat(response.transcript()).isEqualTo("거래내역 알려줘");
        assertThat(response.history().totalCount()).isEqualTo(2L);
        assertThat(response.history().items()).hasSize(2);
        assertThat(response.toVoiceMessage())
                .contains("생활비 통장")
                .contains("거래가 2건 있어요")
                .contains("엄마 님에게 5만원 보냈어요")
                .contains("회사 님에게서 3백만원 받았어요");
        assertThat(response.toVoiceMessage()).doesNotContain("50000");
    }

    @Test
    @DisplayName("조회 기간에 거래가 없으면 오류가 아니라 내역이 없다고 안내한다")
    void 조회_기간에_거래가_없으면_오류가_아니라_내역이_없다고_안내한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createHistoryAnalysis(null, null));
        given(account.getId()).willReturn(12L);
        given(account.isActive()).willReturn(true);
        given(account.toVoiceName()).willReturn("생활비 통장");
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(transactionQueryService.findAll(
                eq(USER_ID), eq(12L), any(), any(), eq(null), eq(0), anyInt()
        )).willReturn(PageResponse.of(List.of(), 0, 5, 0L));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(response.history().items()).isEmpty();
        assertThat(response.toVoiceMessage()).contains("거래 내역이 없어요");
    }

    @Test
    @DisplayName("재질문 중 거래내역을 물으면 이전 송금 슬롯을 폐기하고 명령 대기로 돌아간다")
    void 재질문_중_거래내역을_물으면_이전_송금_슬롯을_폐기하고_명령_대기로_돌아간다() throws Exception {
        // given
        final VoiceSession session = createSession();
        session.clarify(
                VoiceIntent.TRANSFER,
                new ObjectMapper().writeValueAsString(
                        PendingTransferSlots.clarifying(50000L, null, null)
                ),
                LocalDateTime.now()
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createHistoryAnalysis(null, null));
        given(account.getId()).willReturn(12L);
        given(account.isActive()).willReturn(true);
        given(account.toVoiceName()).willReturn("생활비 통장");
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(transactionQueryService.findAll(
                eq(USER_ID), eq(12L), any(), any(), eq(null), eq(0), anyInt()
        )).willReturn(PageResponse.of(List.of(), 0, 5, 0L));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.ACTIVE);
        assertThat(session.getPendingSlots()).isNull();
        assertThat(session.getPendingIntent()).isNull();
        assertThat(session.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("미래 기간의 거래내역을 요청하면 조회하지 않고 거부한다")
    void 미래_기간의_거래내역을_요청하면_조회하지_않고_거부한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createHistoryAnalysis(LocalDate.now().plusDays(1), null));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HISTORY_PERIOD_INVALID);
        then(transactionQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기본 계좌가 없으면 거래내역을 조회하지 않는다")
    void 기본_계좌가_없으면_거래내역을_조회하지_않는다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createHistoryAnalysis(null, null));
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID)).willReturn(Optional.empty());
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRIMARY_ACCOUNT_NOT_SET);
        then(transactionQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잔액 음성 명령을 처리하면 기본 계좌 잔액을 한국어로 안내한다")
    void 잔액_음성_명령을_처리하면_기본_계좌_잔액을_한국어로_안내한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createBalanceAnalysis(null, null));
        given(balanceInquiryService.inquire(USER_ID, null)).willReturn(new BalanceResponse(
                12L,
                "국민은행",
                "생활비 통장",
                53_000L,
                53_000L,
                LocalDateTime.of(2026, 8, 25, 10, 0)
        ));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(response.intent()).isEqualTo(VoiceIntent.BALANCE);
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.ACTIVE);
        assertThat(response.transcript()).isEqualTo("잔액 알려줘");
        assertThat(response.balance().balanceAmount()).isEqualTo(53_000L);
        assertThat(response.toVoiceMessage()).isEqualTo("국민은행 생활비 통장에 5만 3천원 있어요.");
        assertThat(response.toVoiceMessage()).doesNotContain("53000");
    }

    @Test
    @DisplayName("별칭을 말하면 해당 계좌의 잔액을 조회한다")
    void 별칭을_말하면_해당_계좌의_잔액을_조회한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createBalanceAnalysis("월급통장", HIGH_CONFIDENCE));
        given(balanceInquiryService.inquire(USER_ID, "월급통장")).willReturn(new BalanceResponse(
                13L,
                "신한은행",
                "월급통장",
                1_200_000L,
                1_200_000L,
                LocalDateTime.of(2026, 8, 25, 10, 0)
        ));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        final VoiceCommandResponse response = service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(response.balance().accountId()).isEqualTo(13L);
        assertThat(response.toVoiceMessage()).contains("월급통장").contains("1백20만원");
        then(balanceInquiryService).should().inquire(USER_ID, "월급통장");
    }

    @Test
    @DisplayName("계좌 별칭 신뢰도가 낮으면 조회하지 않고 재발화를 요청한다")
    void 계좌_별칭_신뢰도가_낮으면_조회하지_않고_재발화를_요청한다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createBalanceAnalysis("월급통장", new BigDecimal("0.42")));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOW_CONFIDENCE);
        then(balanceInquiryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잔액조회가 실패하면 0원으로 안내하지 않고 실패를 그대로 알린다")
    void 잔액조회가_실패하면_0원으로_안내하지_않고_실패를_그대로_알린다() {
        // given
        final VoiceSession session = createSession();
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createBalanceAnalysis(null, null));
        given(balanceInquiryService.inquire(USER_ID, null))
                .willThrow(new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED));
        final VoiceCommandService service = createService();

        // when & then
        assertThatThrownBy(() -> service.process(USER_ID, SESSION_ID, createAudio()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_INQUIRY_FAILED);
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("재질문 중 잔액을 물으면 이전 송금 슬롯을 폐기하고 명령 대기로 돌아간다")
    void 재질문_중_잔액을_물으면_이전_송금_슬롯을_폐기하고_명령_대기로_돌아간다() throws Exception {
        // given
        final VoiceSession session = createSession();
        session.clarify(
                VoiceIntent.TRANSFER,
                new ObjectMapper().writeValueAsString(
                        PendingTransferSlots.clarifying(50000L, null, null)
                ),
                LocalDateTime.now()
        );
        given(voiceSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willReturn(createBalanceAnalysis(null, null));
        given(balanceInquiryService.inquire(USER_ID, null)).willReturn(new BalanceResponse(
                12L,
                "국민은행",
                "생활비 통장",
                53_000L,
                53_000L,
                LocalDateTime.of(2026, 8, 25, 10, 0)
        ));
        given(voiceCommandRepository.save(any(VoiceCommand.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        final VoiceCommandService service = createService();

        // when
        service.process(USER_ID, SESSION_ID, createAudio());

        // then
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.ACTIVE);
        assertThat(session.getPendingSlots()).isNull();
        assertThat(session.getPendingIntent()).isNull();
        assertThat(session.getRetryCount()).isZero();
    }

    private VoiceAnalysisResponse createBalanceAnalysis(
            final String sourceAccountAlias,
            final BigDecimal aliasConfidence
    ) {
        return VoiceAnalysisResponse.of(
                "voice-balance",
                SESSION_ID,
                "잔액 알려줘",
                HIGH_CONFIDENCE,
                VoiceIntent.BALANCE,
                HIGH_CONFIDENCE,
                new VoiceEntities(null, null, sourceAccountAlias, null, null, null),
                new VoiceEntityConfidences(null, null, aliasConfidence, null, null, null),
                List.of(),
                85
        );
    }

    private VoiceAnalysisResponse createHistoryAnalysis(
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        return VoiceAnalysisResponse.of(
                "voice-history",
                SESSION_ID,
                "거래내역 알려줘",
                HIGH_CONFIDENCE,
                VoiceIntent.HISTORY,
                HIGH_CONFIDENCE,
                new VoiceEntities(null, null, null, null, startDate, endDate),
                VoiceEntityConfidences.transfer(null, null, null),
                List.of(),
                90
        );
    }

    private TransactionResponse createTransaction(
            final Long transactionId,
            final TransactionType type,
            final Long amount,
            final String counterpartyName,
            final int dayOfMonth
    ) {
        return new TransactionResponse(
                transactionId,
                12L,
                type,
                amount,
                null,
                counterpartyName,
                null,
                LocalDateTime.of(2026, 8, dayOfMonth, 10, 0),
                null,
                null,
                null
        );
    }

    private VoiceCommandService createService() {
        return new VoiceCommandService(
                voiceSessionRepository,
                new VoiceSessionExpirationService(voiceSessionRepository),
                voiceCommandRepository,
                voiceAnalysisClient,
                transferValidationService,
                transferExecutionService,
                transactionQueryService,
                balanceInquiryService,
                new TransferTargetResolver(accountRepository, transferRecipientRepository),
                new SpokenAccountNumberParser(),
                new BankDirectory(),
                new ObjectMapper(),
                audioDurationValidator
        );
    }

    private VoiceSession createSession() {
        return createSession(USER_ID);
    }

    private VoiceSession createAwaitingConfirmationSession() throws Exception {
        final VoiceSession session = createSession();
        session.awaitConfirmation(
                VoiceIntent.TRANSFER,
                new ObjectMapper().writeValueAsString(PendingTransferSlots.awaitingConfirmation(
                        50_000L,
                        "엄마",
                        null,
                        8L,
                        12L,
                        "confirmation-123"
                )),
                LocalDateTime.now()
        );
        return session;
    }

    private VoiceAnalysisResponse createConfirmationAnalysis() {
        return VoiceAnalysisResponse.of(
                "voice-confirm",
                SESSION_ID,
                "응 보내줘",
                HIGH_CONFIDENCE,
                VoiceIntent.CONFIRM,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(null, null, null),
                VoiceEntityConfidences.transfer(null, null, null),
                List.of(),
                75
        );
    }

    private VoiceSession createSession(final Long userId) {
        final User user = User.builder()
                .name("사용자")
                .phone("encrypted-phone")
                .birthDate(LocalDate.of(1950, 1, 1))
                .userType(UserType.SENIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        final VoiceSession session = VoiceSession.builder()
                .user(user)
                .channel(VoiceChannel.APP)
                .build();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private MockMultipartFile createAudio() {
        return new MockMultipartFile(
                "audio",
                "voice.webm",
                "audio/webm;codecs=opus",
                new byte[]{1, 2, 3}
        );
    }
}
