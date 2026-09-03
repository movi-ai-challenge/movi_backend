package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 음성 이체에서 <b>무엇이 빠졌는지 사용자에게 짚어 주는지</b>를 본다.
 *
 * <p>화면을 볼 수 없는 사용자에게 재질문 문구는 유일한 안내다. "누구에게 보내시겠어요?"는
 * 이름을 못 들었을 때만 맞는 말이고, 은행이나 계좌번호가 빠진 상황에 같은 문장을 돌려주면
 * 사용자는 무엇을 더 말해야 하는지 알 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceTransferClarificationTest {

    private static final BigDecimal TRUSTED = new BigDecimal("0.95");

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private TransferProperties transferProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private BankDirectory bankDirectory;

    @InjectMocks
    private TransferValidationService transferValidationService;

    /** 이름 기반 시나리오는 금액 검증을 지난 뒤에야 수취인 해석에 닿는다. */
    private void allowAmountRange() {
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
    }

    @Test
    @DisplayName("계좌번호만 말하고 은행을 빠뜨리면 은행을 묻는다")
    void 은행이_빠지면_은행을_묻는다() {
        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, null, "3520123456789", null, null,
                        TRUSTED, TRUSTED, TRUSTED, null
                )
        );

        assertThat(result).isInstanceOf(TransferClarification.class);
        final TransferClarification clarification = (TransferClarification) result;
        assertThat(clarification.voiceMessage())
                .as("계좌번호는 말했는데 '누구에게 보내시겠어요?'를 되물으면 무엇이 빠졌는지 알 수 없다")
                .contains("은행");
    }

    @Test
    @DisplayName("은행만 말하고 계좌번호를 빠뜨리면 계좌번호를 묻는다")
    void 계좌번호가_빠지면_계좌번호를_묻는다() {
        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, null, null, "004", null,
                        TRUSTED, TRUSTED, TRUSTED, null
                )
        );

        assertThat(result).isInstanceOf(TransferClarification.class);
        final TransferClarification clarification = (TransferClarification) result;
        assertThat(clarification.voiceMessage())
                .as("은행은 말했는데 '누구에게 보내시겠어요?'를 되물으면 계좌번호가 빠진 줄 모른다")
                .contains("계좌번호");
    }

    @Test
    @DisplayName("이름도 계좌도 없으면 누구에게 보낼지 묻는다")
    void 아무것도_없으면_수취인을_묻는다() {
        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, null, null, null, null,
                        TRUSTED, TRUSTED, TRUSTED, null
                )
        );

        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).missingSlots())
                .containsExactly(TransferSlot.RECIPIENT);
    }

    @Test
    @DisplayName("등록되지 않은 이름을 부르면 계좌번호를 말해 달라고 안내한다")
    void 등록되지_않은_이름이면_계좌번호를_요청한다() {
        allowAmountRange();
        given(transferRecipientRepository.findByUserIdAndNickname(1L, "김철수"))
                .willReturn(Optional.empty());
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(1L))
                .willReturn(List.of());

        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, "김철수", null, null, null,
                        TRUSTED, TRUSTED, TRUSTED, TRUSTED
                )
        );

        assertThat(result)
                .as("'저장된 분이 없어요'로 끝내면 다음에 무엇을 말해야 하는지 알 수 없다")
                .isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage()).contains("계좌번호");
    }

    @Test
    @DisplayName("비슷한 이름이 여럿이면 저장이 안 됐다고 하지 않고 계좌번호로 가린다")
    void 동명이인이면_계좌번호로_가린다() {
        allowAmountRange();
        given(transferRecipientRepository.findByUserIdAndNickname(1L, "김영희"))
                .willReturn(Optional.empty());
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(1L))
                .willReturn(List.of(recipient("김영호"), recipient("김영하")));

        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, "김영희", null, null, null,
                        TRUSTED, TRUSTED, TRUSTED, TRUSTED
                )
        );

        assertThat(result)
                .as("등록은 돼 있는데 누구인지 못 고른 상황이다. '없어요'는 사실과 다르다")
                .isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage()).contains("계좌번호");
    }

    private TransferRecipient recipient(final String nickname) {
        final User user = User.builder().name("소유자").userType(UserType.GENERAL).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        final TransferRecipient recipient = TransferRecipient.builder()
                .user(user)
                .nickname(nickname)
                .bankCode("004")
                .accountNum("encrypted")
                .accountNumHash("hash-" + nickname)
                .holderName(nickname)
                .build();
        ReflectionTestUtils.setField(recipient, "id", (long) nickname.hashCode());
        return recipient;
    }

}
