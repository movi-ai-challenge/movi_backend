package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferValidationServiceTest {

    private static final Long USER_ID = 3L;
    private static final BigDecimal TRUSTED_CONFIDENCE = new BigDecimal("0.90");

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private TransferProperties transferProperties;

    @Mock
    private TransferRecipient transferRecipient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @org.mockito.Spy
    private BankDirectory bankDirectory = new BankDirectory();

    @InjectMocks
    private TransferValidationService transferValidationService;

    @Test
    @DisplayName("신뢰할 수 있는 이체 명령을 검증하면 등록 수취인과 정규화된 정보를 반환한다")
    void 신뢰할_수_있는_이체_명령을_검증하면_등록_수취인과_정규화된_정보를_반환한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                "  엄마  ",
                "  생활비 통장  ",
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findByUserIdAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(transferRecipient));

        // when
        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        // then
        assertThat(result).isInstanceOfSatisfying(ValidatedTransferCommand.class, validated -> {
            assertThat(validated.amount()).isEqualTo(50_000L);
            assertThat(validated.recipient()).isEqualTo(transferRecipient);
            assertThat(validated.sourceAccountAlias()).isEqualTo("생활비 통장");
        });
    }

    @Test
    @DisplayName("수취인과 금액이 모두 누락되면 수취인을 먼저 재질문한다")
    void 수취인과_금액이_모두_누락되면_수취인을_먼저_재질문한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                null,
                null,
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                null,
                null
        );

        // when
        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        // then
        assertThat(result).isInstanceOfSatisfying(TransferClarification.class, clarification -> {
            assertThat(clarification.missingSlots())
                    .containsExactly(TransferSlot.RECIPIENT, TransferSlot.AMOUNT);
            assertThat(clarification.voiceMessage()).isEqualTo("누구에게 보내시겠어요?");
        });
    }

    @Test
    @DisplayName("금액의 개별 신뢰도가 낮으면 금액을 누락값으로 처리한다")
    void 금액의_개별_신뢰도가_낮으면_금액을_누락값으로_처리한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                "엄마",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                new BigDecimal("0.79"),
                TRUSTED_CONFIDENCE
        );

        // when
        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        // then
        assertThat(result).isInstanceOfSatisfying(TransferClarification.class, clarification -> {
            assertThat(clarification.missingSlots()).containsExactly(TransferSlot.AMOUNT);
            assertThat(clarification.voiceMessage()).isEqualTo("얼마를 보내시겠어요?");
        });
    }

    @Test
    @DisplayName("전체 음성 신뢰도가 낮으면 재발화 요청 예외가 발생한다")
    void 전체_음성_신뢰도가_낮으면_재발화_요청_예외가_발생한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                "엄마",
                null,
                new BigDecimal("0.59"),
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE
        );

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOW_CONFIDENCE);
    }

    @Test
    @DisplayName("최소 금액보다 작은 이체 명령을 검증하면 금액 예외가 발생한다")
    void 최소_금액보다_작은_이체_명령을_검증하면_금액_예외가_발생한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                0L,
                "엄마",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("1회 한도를 넘는 이체 명령을 검증하면 한도 초과 예외가 발생한다")
    void 일회_한도를_넘는_이체_명령을_검증하면_한도_초과_예외가_발생한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                1_000_001L,
                "엄마",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("등록되지 않은 수취인으로 이체하면 수취인 조회 예외가 발생한다")
    void 등록되지_않은_수취인으로_이체하면_수취인_조회_예외가_발생한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                "모르는 사람",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findByUserIdAndNickname(USER_ID, "모르는 사람"))
                .willReturn(Optional.empty());
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(List.of());

        // when
        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        // then — 예외로 끊으면 "저장된 분이 없어요"에서 대화가 끝난다. 계좌번호를 말하면
        //        보낼 수 있다는 것을 알려 줘야 사용자가 다음 말을 할 수 있다.
        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage())
                .contains("모르는 사람")
                .contains("계좌번호");
    }

    @Test
    @DisplayName("STT가 주혁을 주역으로 들으면 유일한 등록 별칭 주혁으로 보정한다")
    void 비슷하게_인식된_이름을_유일한_등록_별칭으로_보정한다() {
        final TransferCommandRequest command = TransferCommandRequest.of(
                30_000L, "주역", null,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findByUserIdAndNickname(USER_ID, "주역"))
                .willReturn(Optional.empty());
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(List.of(transferRecipient));
        given(transferRecipient.getNickname()).willReturn("주혁");

        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        assertThat(result).isInstanceOfSatisfying(ValidatedTransferCommand.class, validated ->
                assertThat(validated.recipient()).isSameAs(transferRecipient));
    }

    @Test
    @DisplayName("비슷한 등록 별칭이 둘이면 오송금을 막기 위해 임의로 고르지 않는다")
    void 비슷한_등록_별칭이_둘이면_보정하지_않는다() {
        final TransferRecipient otherRecipient = org.mockito.Mockito.mock(TransferRecipient.class);
        final TransferCommandRequest command = TransferCommandRequest.of(
                30_000L, "주역", null,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findByUserIdAndNickname(USER_ID, "주역"))
                .willReturn(Optional.empty());
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(List.of(transferRecipient, otherRecipient));
        given(transferRecipient.getNickname()).willReturn("주혁");
        given(otherRecipient.getNickname()).willReturn("주억");

        final TransferValidationResult result = transferValidationService.validate(USER_ID, command);

        // 임의로 고르지 않는 것은 그대로다. 다만 등록은 돼 있으므로 "저장된 분이 없어요"가
        // 아니라, 계좌번호로 누구인지 가려 달라고 되묻는다.
        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage())
                .contains("여러 개")
                .contains("계좌번호");
    }

    @Test
    @DisplayName("계좌번호를 말하면 등록하지 않은 상대에게도 보낼 수 있다")
    void 계좌번호를_말하면_등록하지_않은_상대에게도_보낼_수_있다() {
        // given - 이름은 없고 계좌번호만 있다.
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                null,
                "3522315749",
                "011",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                null
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(java.util.List.of());
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(org.mockito.Mockito.mock(
                        com.movi_backend.domain.auth.entity.User.class)));
        given(sensitiveDataCrypto.encrypt("3522315749")).willReturn("encrypted");
        given(transferRecipientRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(transferRecipient);

        // when
        final TransferValidationResult result =
                transferValidationService.validate(USER_ID, command);

        // then - 이름이 없어도 재질문하지 않는다.
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
        assertThat(((ValidatedTransferCommand) result).recipient()).isSameAs(transferRecipient);
    }

    @Test
    @DisplayName("같은 계좌로 다시 보내면 이미 만들어 둔 수취인을 쓴다 - 재이체로 평가되게 한다")
    void 같은_계좌로_다시_보내면_기존_수취인을_쓴다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                null,
                "3522315749",
                "011",
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                null
        );
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipient.getBankCode()).willReturn("011");
        given(transferRecipient.getAccountNum()).willReturn("encrypted");
        given(sensitiveDataCrypto.decrypt("encrypted")).willReturn("3522315749");
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(java.util.List.of(transferRecipient));

        // when
        final TransferValidationResult result =
                transferValidationService.validate(USER_ID, command);

        // then - 새로 만들지 않는다.
        assertThat(((ValidatedTransferCommand) result).recipient()).isSameAs(transferRecipient);
        org.mockito.BDDMockito.then(transferRecipientRepository)
                .should(org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("뒤 네 자리가 같은 계좌가 이미 있으면 별칭에 번호를 붙인다 - 그대로 저장하면 이체가 서버 오류로 끝난다")
    void 별칭이_겹치면_번호를_붙인다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L, null, "3522315749143", "011", null,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE, null);
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(USER_ID))
                .willReturn(java.util.List.of());
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(org.mockito.Mockito.mock(
                        com.movi_backend.domain.auth.entity.User.class)));
        given(sensitiveDataCrypto.encrypt("3522315749143")).willReturn("encrypted");
        given(transferRecipientRepository.existsByUserIdAndNickname(USER_ID, "농협은행 9143"))
                .willReturn(true);
        given(transferRecipientRepository.existsByUserIdAndNickname(USER_ID, "농협은행 9143 (2)"))
                .willReturn(false);
        given(transferRecipientRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(transferRecipient);

        // when
        transferValidationService.validate(USER_ID, command);

        // then
        final org.mockito.ArgumentCaptor<TransferRecipient> saved =
                org.mockito.ArgumentCaptor.forClass(TransferRecipient.class);
        org.mockito.BDDMockito.then(transferRecipientRepository).should().save(saved.capture());
        assertThat(saved.getValue().getNickname()).isEqualTo("농협은행 9143 (2)");
    }

    @Test
    @DisplayName("계좌번호도 이름도 없으면 누구에게 보낼지 되묻는다")
    void 계좌번호도_이름도_없으면_되묻는다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L,
                null,
                null,
                null,
                null,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE,
                null
        );

        // when
        final TransferValidationResult result =
                transferValidationService.validate(USER_ID, command);

        // then
        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).missingSlots())
                .contains(TransferSlot.RECIPIENT);
    }
}
