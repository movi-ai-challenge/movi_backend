package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.TransferValidationService;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransferSlot;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private AccountRepository accountRepository;

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
                "엄마한테 보내줘",
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
        assertThat(response.missingSlots()).containsExactly(TransferSlot.AMOUNT);
        assertThat(response.toVoiceMessage()).isEqualTo("얼마를 보내시겠어요?");
        assertThat(session.getPendingSlots()).contains("엄마");
        assertThat(session.getPendingSlots()).contains("\"amount\":null");

        final ArgumentCaptor<VoiceCommand> commandCaptor =
                ArgumentCaptor.forClass(VoiceCommand.class);
        then(voiceCommandRepository).should().save(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getStatus().name()).isEqualTo("CLARIFY");
        assertThat(commandCaptor.getValue().getProcessingMs()).isEqualTo(120);
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
                voiceCommandRepository,
                voiceAnalysisClient,
                transferValidationService,
                accountRepository,
                objectMapper
        );

        // when
        final VoiceCommandResponse response = service.process(
                USER_ID,
                SESSION_ID,
                createAudio()
        );

        // then
        assertThat(response.state()).isEqualTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
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

    private VoiceCommandService createService() {
        return new VoiceCommandService(
                voiceSessionRepository,
                voiceCommandRepository,
                voiceAnalysisClient,
                transferValidationService,
                accountRepository,
                new ObjectMapper()
        );
    }

    private VoiceSession createSession() {
        return createSession(USER_ID);
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
