package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 등록되지 않은 상대에게 계좌번호로 보낼 때 <b>사용자가 부른 이름을 남기는지</b> 본다.
 *
 * <p>수취인 목록은 화면을 볼 수 없는 사용자가 계좌번호를 외우지 않고 송금할 유일한
 * 수단이다. "국민은행 6789"로 저장하면 다음에 부를 수 없고, 지우거나 이름을 바꿀 방법도
 * 없어 그대로 남는다.
 */
@ExtendWith(MockitoExtension.class)
class SpokenRecipientNameTest {

    private static final BigDecimal TRUSTED = new BigDecimal("0.95");
    private static final BigDecimal UNTRUSTED = new BigDecimal("0.40");
    private static final String ACCOUNT_NUMBER = "3520123456789";

    @Mock private TransferRecipientRepository transferRecipientRepository;
    @Mock private TransferProperties transferProperties;
    @Mock private UserRepository userRepository;
    @Mock private SensitiveDataCrypto sensitiveDataCrypto;
    @Mock private BankDirectory bankDirectory;
    @InjectMocks private TransferValidationService transferValidationService;

    @BeforeEach
    void allowTransfer() {
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
        given(transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(1L))
                .willReturn(List.of());
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(sensitiveDataCrypto.encrypt(any())).willReturn("encrypted");
        given(sensitiveDataCrypto.hash(any())).willReturn("hash");
        given(transferRecipientRepository.save(any(TransferRecipient.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("이름을 말했으면 그 이름으로 저장해 다음에 이름만 불러도 보낼 수 있다")
    void 말한_이름으로_저장한다() {
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "김철수"))
                .willReturn(false);

        final TransferRecipient saved = register("김철수", TRUSTED);

        assertThat(saved.getNickname()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("예금주는 사용자가 부른 이름으로 단정하지 않는다")
    void 예금주는_부른_이름으로_단정하지_않는다() {
        // given — 실명을 확인할 방법이 없다. 아는 사실(은행·뒤 네 자리)만 적는다.
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "김철수"))
                .willReturn(false);
        given(bankDirectory.displayNameOf("004")).willReturn("국민은행");

        final TransferRecipient saved = register("김철수", TRUSTED);

        assertThat(saved.getHolderName()).isEqualTo("국민은행 6789");
    }

    @Test
    @DisplayName("같은 이름이 이미 있으면 뒤에 번호를 붙여 저장이 실패하지 않게 한다")
    void 같은_이름이_있으면_번호를_붙인다() {
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "김철수"))
                .willReturn(true);
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "김철수 (2)"))
                .willReturn(false);

        final TransferRecipient saved = register("김철수", TRUSTED);

        assertThat(saved.getNickname()).isEqualTo("김철수 (2)");
    }

    @Test
    @DisplayName("이름을 말하지 않았으면 은행과 뒤 네 자리로 만든다")
    void 이름이_없으면_은행과_뒤_네_자리로_만든다() {
        given(bankDirectory.displayNameOf("004")).willReturn("국민은행");
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "국민은행 6789"))
                .willReturn(false);

        final TransferRecipient saved = register(null, null);

        assertThat(saved.getNickname()).isEqualTo("국민은행 6789");
    }

    @Test
    @DisplayName("잘못 들었을 수 있는 이름은 쓰지 않는다")
    void 신뢰도가_낮은_이름은_쓰지_않는다() {
        // given — 신뢰도가 낮은 이름을 그대로 목록에 박으면 되돌릴 수단이 없다
        given(bankDirectory.displayNameOf("004")).willReturn("국민은행");
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "국민은행 6789"))
                .willReturn(false);

        final TransferRecipient saved = register("김첨수", UNTRUSTED);

        assertThat(saved.getNickname()).isEqualTo("국민은행 6789");
    }

    private TransferRecipient register(final String spokenName, final BigDecimal nameConfidence) {
        final TransferValidationResult result = transferValidationService.validate(
                1L,
                new TransferCommandRequest(
                        50_000L, spokenName, ACCOUNT_NUMBER, "004", null,
                        TRUSTED, TRUSTED, TRUSTED, nameConfidence
                )
        );
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
        return ((ValidatedTransferCommand) result).recipient();
    }

    private User user() {
        final User user = User.builder().name("보내는 사람").userType(UserType.GENERAL).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
