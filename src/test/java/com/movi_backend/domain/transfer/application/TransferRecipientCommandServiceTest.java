package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferRecipientCommandServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransferTargetVerifier transferTargetVerifier;

    @Mock
    private TransferRecipientRegistrar transferRecipientRegistrar;

    @InjectMocks
    private TransferRecipientCommandService transferRecipientCommandService;

    @Test
    @DisplayName("예금주가 확인되면 확인된 이름으로 주소록에 등록한다")
    void 확인된_계좌를_주소록에_등록한다() {
        // given — 사용자는 이름과 은행·계좌번호만 보낸다. 예금주는 조회로 채운다
        final VerifiedTransferTarget target = VerifiedTransferTarget.of(
                "004", "004987654321", "hash-son", "김민수");
        given(transferTargetVerifier.verifyForRegistration(USER_ID, "004", "004-9876-54321"))
                .willReturn(target);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(transferRecipientRegistrar.registerAddressBookEntry(
                Mockito.any(User.class),
                Mockito.eq("아들"),
                Mockito.eq(target),
                Mockito.any(LocalDateTime.class)))
                .willReturn(recipient("아들", "김민수"));

        // when
        final RecipientResponse response = transferRecipientCommandService.register(
                USER_ID,
                new RecipientRegisterRequest("  아들  ", "004", "004-9876-54321")
        );

        // then — 응답의 예금주는 사용자가 적은 값이 아니라 조회로 확인된 이름이다
        assertThat(response.nickname()).isEqualTo("아들");
        assertThat(response.holderName()).isEqualTo("김민수");
        assertThat(response.maskedAccountNumber()).doesNotContain("004987654321");
    }

    @Test
    @DisplayName("확인되지 않은 계좌는 등록하지 않는다")
    void 확인되지_않은_계좌는_등록하지_않는다() {
        // given
        given(transferTargetVerifier.verifyForRegistration(USER_ID, "004", "004000000000"))
                .willThrow(new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED));

        // when & then
        assertThatThrownBy(() -> transferRecipientCommandService.register(
                USER_ID,
                new RecipientRegisterRequest("아들", "004", "004000000000")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED);

        then(transferRecipientRegistrar).should(Mockito.never()).registerAddressBookEntry(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("공백만 있는 이름으로는 등록할 수 없다")
    void 공백_이름으로는_등록할_수_없다() {
        // when & then
        assertThatThrownBy(() -> transferRecipientCommandService.register(
                USER_ID,
                new RecipientRegisterRequest("   ", "004", "004987654321")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private User user() {
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted")
                .phoneHash("hash")
                .birthDate(LocalDate.of(1950, 3, 2))
                .userType(UserType.SENIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private TransferRecipient recipient(final String nickname, final String holderName) {
        final TransferRecipient recipient = TransferRecipient.builder()
                .user(user())
                .nickname(nickname)
                .bankCode("004")
                .accountNum("encrypted")
                .accountNumHash("hash-son")
                .holderName(holderName)
                .addressBook(true)
                .verifiedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(recipient, "id", 300L);
        return recipient;
    }
}
