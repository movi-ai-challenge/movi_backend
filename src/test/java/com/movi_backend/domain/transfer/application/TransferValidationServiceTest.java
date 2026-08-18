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
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
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

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND);
    }
}
