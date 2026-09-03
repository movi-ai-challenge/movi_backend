package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferRecipientRegistrarTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 10, 0);

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private TransferRecipientRegistrar transferRecipientRegistrar;

    @Test
    @DisplayName("등록하지 않은 계좌로 보내면 주소록에 나오지 않는 거래 상대 행이 만들어진다")
    void 일회성_송금_대상은_주소록에_올라가지_않는다() {
        // given
        final User user = user();
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "004", "hash-new"))
                .willReturn(Optional.empty());
        given(sensitiveDataCrypto.encrypt("004987654321")).willReturn("encrypted");
        given(transferRecipientRepository.saveAndFlush(Mockito.any(TransferRecipient.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        final TransferRecipient saved = transferRecipientRegistrar.resolveTransferTarget(
                user, target("004", "004987654321", "hash-new", "김민수"), NOW);

        // then — 사용자가 짓지 않은 이름을 만들어 붙이지 않는다
        assertThat(saved.getNickname()).isNull();
        assertThat(saved.isAddressBook()).isFalse();
        assertThat(saved.isVerified()).isTrue();
        assertThat(saved.getHolderName()).isEqualTo("김민수");
    }

    @Test
    @DisplayName("행을 만들어도 보낸 적이 없으면 FDS 는 처음 보내는 상대로 본다")
    void 행을_만들었다고_기존_거래자가_되지_않는다() {
        // given
        final User user = user();
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "004", "hash-new"))
                .willReturn(Optional.empty());
        given(sensitiveDataCrypto.encrypt("004987654321")).willReturn("encrypted");
        given(transferRecipientRepository.saveAndFlush(Mockito.any(TransferRecipient.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        final TransferRecipient saved = transferRecipientRegistrar.resolveTransferTarget(
                user, target("004", "004987654321", "hash-new", "김민수"), NOW);

        // then
        assertThat(saved.getTransferCount()).isZero();
        assertThat(saved.isFirstTime()).isTrue();
    }

    @Test
    @DisplayName("같은 계좌로 다시 보내면 이미 있는 행을 그대로 쓴다")
    void 같은_계좌는_한_행으로_유지된다() {
        // given — 이미 두 번 보낸 상대
        final User user = user();
        final TransferRecipient existing = recipient(null, false, "004", "hash-new", 2);
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "004", "hash-new"))
                .willReturn(Optional.of(existing));

        // when
        final TransferRecipient resolved = transferRecipientRegistrar.resolveTransferTarget(
                user, target("004", "004987654321", "hash-new", "김민수"), NOW);

        // then — 이체 횟수가 쪼개지지 않는다
        assertThat(resolved).isSameAs(existing);
        assertThat(resolved.getTransferCount()).isEqualTo(2);
        then(transferRecipientRepository).should(Mockito.never())
                .saveAndFlush(Mockito.any(TransferRecipient.class));
    }

    @Test
    @DisplayName("일회성으로 보내던 계좌를 등록하면 같은 행에 이름이 붙는다")
    void 일회성_대상을_주소록으로_올린다() {
        // given — 두 번 보낸 계좌에 "엄마"라는 이름을 붙인다
        final User user = user();
        final TransferRecipient existing = recipient(null, false, "088", "hash-mother", 2);
        given(transferRecipientRepository
                .existsByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(false);
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "088", "hash-mother"))
                .willReturn(Optional.of(existing));

        // when
        final TransferRecipient promoted = transferRecipientRegistrar.registerAddressBookEntry(
                user, "엄마", target("088", "110123456789", "hash-mother", "이영자"), NOW);

        // then — 이체 이력이 남아 있어야 FDS 가 처음 보내는 상대로 보지 않는다
        assertThat(promoted).isSameAs(existing);
        assertThat(promoted.getNickname()).isEqualTo("엄마");
        assertThat(promoted.isAddressBook()).isTrue();
        assertThat(promoted.getTransferCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 다른 이름으로 등록된 계좌는 또 등록할 수 없다")
    void 이미_등록된_계좌는_다시_등록할_수_없다() {
        // given — "엄마"로 등록된 계좌를 "어머니"로 또 등록하려는 상황
        final User user = user();
        final TransferRecipient existing = recipient("엄마", true, "088", "hash-mother", 5);
        given(transferRecipientRepository
                .existsByUserIdAndAddressBookTrueAndNickname(USER_ID, "어머니"))
                .willReturn(false);
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "088", "hash-mother"))
                .willReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> transferRecipientRegistrar.registerAddressBookEntry(
                user, "어머니", target("088", "110123456789", "hash-mother", "이영자"), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED);
    }

    @Test
    @DisplayName("이미 쓰고 있는 이름으로는 등록할 수 없다")
    void 이미_쓰는_이름으로는_등록할_수_없다() {
        // given
        final User user = user();
        given(transferRecipientRepository
                .existsByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> transferRecipientRegistrar.registerAddressBookEntry(
                user, "엄마", target("004", "004987654321", "hash-new", "김민수"), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("같은 계좌를 동시에 등록하면 500 이 아니라 중복 안내로 끝난다")
    void 동시_등록은_중복_안내로_끝난다() {
        // given — 조회 시점에는 없었는데 저장 직전에 다른 요청이 먼저 넣은 상황
        final User user = user();
        given(transferRecipientRepository
                .existsByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(false);
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "088", "hash-mother"))
                .willReturn(Optional.empty());
        given(sensitiveDataCrypto.encrypt("110123456789")).willReturn("encrypted");
        given(transferRecipientRepository.saveAndFlush(Mockito.any(TransferRecipient.class)))
                .willThrow(new DataIntegrityViolationException("uk_recipient_user_bank_account"));

        // when & then
        assertThatThrownBy(() -> transferRecipientRegistrar.registerAddressBookEntry(
                user, "엄마", target("088", "110123456789", "hash-mother", "이영자"), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED);
    }

    @Test
    @DisplayName("은행이 다르면 계좌번호가 같아도 다른 상대로 저장된다")
    void 다른_은행의_같은_계좌번호는_다른_상대다() {
        // given — 국민은행에 같은 번호가 이미 있지만 신한은행 조회는 비어 있다
        final User user = user();
        given(transferRecipientRepository
                .findByUserIdAndBankCodeAndAccountNumHash(USER_ID, "088", "hash-same"))
                .willReturn(Optional.empty());
        given(sensitiveDataCrypto.encrypt("110123456789")).willReturn("encrypted");
        given(transferRecipientRepository.saveAndFlush(Mockito.any(TransferRecipient.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        transferRecipientRegistrar.resolveTransferTarget(
                user, target("088", "110123456789", "hash-same", "이영자"), NOW);

        // then — 조회 기준에 은행코드가 들어가 다른 은행 행과 섞이지 않는다
        final ArgumentCaptor<TransferRecipient> captor =
                ArgumentCaptor.forClass(TransferRecipient.class);
        then(transferRecipientRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBankCode()).isEqualTo("088");
    }

    private VerifiedTransferTarget target(
            final String bankCode,
            final String accountNumber,
            final String hash,
            final String holderName
    ) {
        return VerifiedTransferTarget.of(bankCode, accountNumber, hash, holderName);
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

    private TransferRecipient recipient(
            final String nickname,
            final boolean addressBook,
            final String bankCode,
            final String hash,
            final int transferCount
    ) {
        final TransferRecipient recipient = TransferRecipient.builder()
                .user(user())
                .nickname(nickname)
                .bankCode(bankCode)
                .accountNum("encrypted")
                .accountNumHash(hash)
                .holderName("이영자")
                .addressBook(addressBook)
                .verifiedAt(NOW.minusDays(1))
                .build();
        ReflectionTestUtils.setField(recipient, "id", 100L);
        for (int count = 0; count < transferCount; count++) {
            recipient.recordTransfer(NOW.minusDays(transferCount - count));
        }
        return recipient;
    }
}
