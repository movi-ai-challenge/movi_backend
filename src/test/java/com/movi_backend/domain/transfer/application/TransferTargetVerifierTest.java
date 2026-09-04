package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.account.application.InternalAccountLocator;
import com.movi_backend.domain.account.application.port.AccountHolderInquiryPort;
import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferTargetVerifierTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AccountHolderInquiryPort accountHolderInquiryPort;

    @Mock
    private InternalAccountLocator internalAccountLocator;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private TransferTargetVerifier transferTargetVerifier;

    @Test
    @DisplayName("은행과 전체 계좌번호가 확인되면 검증된 대상을 만든다")
    void 확인된_계좌는_검증된_대상이_된다() {
        // given
        given(accountHolderInquiryPort.inquire("004", "004987654321"))
                .willReturn(Optional.of(
                        VerifiedAccountHolder.of("004", "004987654321", "김민수")));
        given(internalAccountLocator.locate("004", "004987654321"))
                .willReturn(Optional.empty());
        given(sensitiveDataCrypto.hash("004987654321")).willReturn("hash-son");

        // when
        final VerifiedTransferTarget target = transferTargetVerifier.verifyForTransfer(
                USER_ID, "004", "004-9876-54321");

        // then — 하이픈이 섞여 있어도 숫자만 남겨 조회한다
        assertThat(target.accountNumber()).isEqualTo("004987654321");
        assertThat(target.holderName()).isEqualTo("김민수");
        assertThat(target.accountNumHash()).isEqualTo("hash-son");
        assertThat(target.lastFourDigits()).isEqualTo("4321");
    }

    @Test
    @DisplayName("앞자리만 같고 뒷자리가 다른 계좌번호는 확인되지 않아 통과하지 못한다")
    void 앞자리만_같은_계좌번호는_통과하지_못한다() {
        // given — 004987654321 은 실재하지만 004987650000 은 실재하지 않는다
        given(accountHolderInquiryPort.inquire("004", "004987650000"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004987650000"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED);
    }

    @Test
    @DisplayName("하이픈만 있고 숫자가 없는 입력은 잘못된 계좌번호로 안내한다")
    void 숫자가_없는_계좌번호는_잘못된_입력이다() {
        // given — 형식 정규식만으로는 통과하지만 숫자가 하나도 없다

        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForTransfer(USER_ID, "004", "------"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("문자가 섞인 계좌번호는 잘못된 계좌번호로 안내한다")
    void 문자가_섞인_계좌번호는_잘못된_입력이다() {
        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004-abcd-1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("은행을 말하지 않으면 어느 은행인지 되묻는다")
    void 은행이_없으면_되묻는다() {
        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForTransfer(USER_ID, null, "004987654321"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANK_CODE_MISSING);
    }

    @Test
    @DisplayName("본인 계좌로는 이체할 수 없다")
    void 본인_계좌로는_이체할_수_없다() {
        // given
        given(accountHolderInquiryPort.inquire("004", "12345678901231"))
                .willReturn(Optional.of(
                        VerifiedAccountHolder.of("004", "12345678901231", "김철수")));
        given(internalAccountLocator.locate("004", "12345678901231"))
                .willReturn(Optional.of(accountOf(USER_ID)));

        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForTransfer(USER_ID, "004", "12345678901231"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
    }

    @Test
    @DisplayName("본인 계좌는 주소록에도 등록할 수 없다")
    void 본인_계좌는_주소록에_등록할_수_없다() {
        // given
        given(accountHolderInquiryPort.inquire("004", "12345678901231"))
                .willReturn(Optional.of(
                        VerifiedAccountHolder.of("004", "12345678901231", "김철수")));
        given(internalAccountLocator.locate("004", "12345678901231"))
                .willReturn(Optional.of(accountOf(USER_ID)));

        // when & then
        assertThatThrownBy(() ->
                transferTargetVerifier.verifyForRegistration(USER_ID, "004", "12345678901231"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_RECIPIENT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("다른 사용자의 계좌는 본인 계좌가 아니므로 이체할 수 있다")
    void 다른_사용자의_계좌로는_이체할_수_있다() {
        // given
        given(accountHolderInquiryPort.inquire("011", "35211112299"))
                .willReturn(Optional.of(
                        VerifiedAccountHolder.of("011", "35211112299", "이순자")));
        given(internalAccountLocator.locate("011", "35211112299"))
                .willReturn(Optional.of(accountOf(2L)));
        given(sensitiveDataCrypto.hash("35211112299")).willReturn("hash-other");

        // when
        final VerifiedTransferTarget target = transferTargetVerifier.verifyForTransfer(
                USER_ID, "011", "35211112299");

        // then
        assertThat(target.holderName()).isEqualTo("이순자");
    }

    private Account accountOf(final Long userId) {
        final User user = User.builder()
                .name("소유자")
                .phone("encrypted")
                .phoneHash("hash")
                .birthDate(LocalDate.of(1950, 1, 1))
                .userType(UserType.SENIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        final Account account = Account.builder()
                .user(user)
                .fintechUseNum("199000000000000000000001")
                .bankCode("004")
                .bankName("국민은행")
                .accountNumMasked("123456-**-*****1")
                .alias("생활비 통장")
                .accountType(AccountType.DEPOSIT)
                .build();
        ReflectionTestUtils.setField(account, "id", 10L);
        return account;
    }
}
