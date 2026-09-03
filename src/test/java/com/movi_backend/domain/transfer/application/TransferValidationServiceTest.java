package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
class TransferValidationServiceTest {

    private static final Long USER_ID = 3L;
    private static final BigDecimal TRUSTED_CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.50");

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private TransferProperties transferProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private TransferTargetVerifier transferTargetVerifier;

    @Mock
    private TransferRecipientRegistrar transferRecipientRegistrar;

    @InjectMocks
    private TransferValidationService transferValidationService;

    @Test
    @DisplayName("등록된 이름으로 보내면 그 수취인을 그대로 쓴다")
    void 등록된_이름으로_보낸다() {
        // given
        allowAnyAmount();
        final TransferRecipient mother = verifiedRecipient("엄마", "088", "hash-mother");
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(mother));

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byName(50_000L, "  엄마  "));

        // then
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
        assertThat(((ValidatedTransferCommand) result).recipient()).isSameAs(mother);
    }

    @Test
    @DisplayName("한 글자만 다른 이름은 저장된 상대로 자동 선택되지 않는다")
    void 한_글자_다른_이름은_자동_선택되지_않는다() {
        // given — "혁"으로 저장돼 있는데 "형"이라고 들린 상황
        allowAnyAmount();
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "형"))
                .willReturn(Optional.empty());

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byName(50_000L, "형"));

        // then — 추측하지 않고 되묻는다
        assertThat(result).isInstanceOf(TransferClarification.class);
        final TransferClarification clarification = (TransferClarification) result;
        assertThat(clarification.missingSlots()).containsExactly(TransferSlot.RECIPIENT);
        assertThat(clarification.voiceMessage()).contains("저장돼 있지 않아요");
    }

    @Test
    @DisplayName("끝자리 한 자리만 다른 자동 생성 별칭도 자동 선택되지 않는다")
    void 끝자리_한_자리_다른_이름은_자동_선택되지_않는다() {
        // given — 예전 자동 생성 별칭 "국민은행 6788"이 저장돼 있고 "국민은행 6789"라고 말했다
        allowAnyAmount();
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "국민은행 6789"))
                .willReturn(Optional.empty());

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byName(50_000L, "국민은행 6789"));

        // then
        assertThat(result).isInstanceOf(TransferClarification.class);
    }

    @Test
    @DisplayName("등록하지 않은 계좌도 예금주가 확인되면 보낼 수 있다")
    void 미등록_계좌도_확인되면_보낼_수_있다() {
        // given
        allowAnyAmount();
        final VerifiedTransferTarget target = VerifiedTransferTarget.of(
                "004", "004987654321", "hash-son", "김민수");
        given(transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004987654321"))
                .willReturn(target);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        final TransferRecipient oneTime = oneTimeRecipient("004", "hash-son");
        given(transferRecipientRegistrar.resolveTransferTarget(
                Mockito.any(User.class), Mockito.eq(target), Mockito.any(LocalDateTime.class)))
                .willReturn(oneTime);

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byAccount(50_000L, null, "004987654321", "004"));

        // then
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
        assertThat(((ValidatedTransferCommand) result).recipient()).isSameAs(oneTime);
    }

    @Test
    @DisplayName("확인되지 않은 계좌로는 보내지 않는다")
    void 확인되지_않은_계좌로는_보내지_않는다() {
        // given
        allowAnyAmount();
        given(transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004000000000"))
                .willThrow(new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED));

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(
                USER_ID, byAccount(50_000L, null, "004000000000", "004")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED);

        // 확인되지 않았으므로 어떤 행도 만들지 않는다
        then(transferRecipientRegistrar).should(Mockito.never()).resolveTransferTarget(
                Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("저장된 이름과 다른 계좌를 함께 말하면 어느 쪽도 고르지 않고 되묻는다")
    void 이름과_계좌가_어긋나면_되묻는다() {
        // given — "엄마"는 신한은행 계좌로 저장돼 있는데 국민은행 계좌를 함께 말했다
        allowAnyAmount();
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(verifiedRecipient("엄마", "088", "hash-mother")));
        given(transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004987654321"))
                .willReturn(VerifiedTransferTarget.of(
                        "004", "004987654321", "hash-son", "김민수"));

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byAccount(50_000L, "엄마", "004987654321", "004"));

        // then
        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage())
                .contains("저장된 계좌와 다른 계좌");

        // 저장도 하지 않는다
        then(transferRecipientRegistrar).should(Mockito.never()).resolveTransferTarget(
                Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("저장된 이름과 같은 계좌를 함께 말하면 그대로 진행한다")
    void 이름과_계좌가_같으면_진행한다() {
        // given
        allowAnyAmount();
        final TransferRecipient mother = verifiedRecipient("엄마", "088", "hash-mother");
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(mother));
        final VerifiedTransferTarget target = VerifiedTransferTarget.of(
                "088", "110123456789", "hash-mother", "이영자");
        given(transferTargetVerifier.verifyForTransfer(USER_ID, "088", "110123456789"))
                .willReturn(target);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(transferRecipientRegistrar.resolveTransferTarget(
                Mockito.any(User.class), Mockito.eq(target), Mockito.any(LocalDateTime.class)))
                .willReturn(mother);

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byAccount(50_000L, "엄마", "110123456789", "088"));

        // then
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
    }

    @Test
    @DisplayName("예금주명을 말하며 계좌번호를 함께 주면 저장된 별칭이 없어도 진행한다")
    void 예금주명을_말해도_거절하지_않는다() {
        // given — "김민수"는 별칭으로 저장돼 있지 않다. 예금주명을 말한 것이다
        allowAnyAmount();
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "김민수"))
                .willReturn(Optional.empty());
        final VerifiedTransferTarget target = VerifiedTransferTarget.of(
                "004", "004987654321", "hash-son", "김민수");
        given(transferTargetVerifier.verifyForTransfer(USER_ID, "004", "004987654321"))
                .willReturn(target);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(transferRecipientRegistrar.resolveTransferTarget(
                Mockito.any(User.class), Mockito.eq(target), Mockito.any(LocalDateTime.class)))
                .willReturn(oneTimeRecipient("004", "hash-son"));

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byAccount(50_000L, "김민수", "004987654321", "004"));

        // then
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
    }

    @Test
    @DisplayName("확인된 적 없는 저장 수취인은 다시 확인되지 않으면 보낼 수 없다")
    void 확인되지_않은_저장_수취인으로는_보낼_수_없다() {
        // given — 검증 없이 저장되던 시절의 행
        allowAnyAmount();
        final TransferRecipient legacy = unverifiedRecipient("엄마", "088", "hash-mother");
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(legacy));
        given(sensitiveDataCrypto.decrypt("encrypted")).willReturn("110123456789");
        given(transferTargetVerifier.reverify("088", "110123456789"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                transferValidationService.validate(USER_ID, byName(50_000L, "엄마")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPIENT_UNVERIFIED);
    }

    @Test
    @DisplayName("확인된 적 없는 저장 수취인도 다시 확인되면 보낼 수 있다")
    void 다시_확인되면_저장_수취인으로_보낼_수_있다() {
        // given
        allowAnyAmount();
        final TransferRecipient legacy = unverifiedRecipient("엄마", "088", "hash-mother");
        given(transferRecipientRepository
                .findByUserIdAndAddressBookTrueAndNickname(USER_ID, "엄마"))
                .willReturn(Optional.of(legacy));
        given(sensitiveDataCrypto.decrypt("encrypted")).willReturn("110123456789");
        given(transferTargetVerifier.reverify("088", "110123456789"))
                .willReturn(Optional.of(
                        VerifiedAccountHolder.of("088", "110123456789", "이영자")));

        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byName(50_000L, "엄마"));

        // then — 확인 사실이 행에 남아 다음부터는 되묻지 않는다
        assertThat(result).isInstanceOf(ValidatedTransferCommand.class);
        assertThat(legacy.isVerified()).isTrue();
        assertThat(legacy.getHolderName()).isEqualTo("이영자");
    }

    @Test
    @DisplayName("은행만 말하고 계좌번호를 말하지 않으면 계좌번호를 되묻는다")
    void 은행만_말하면_계좌번호를_되묻는다() {
        // when
        final TransferValidationResult result = transferValidationService.validate(
                USER_ID, byAccount(50_000L, null, null, "004"));

        // then
        assertThat(result).isInstanceOf(TransferClarification.class);
        assertThat(((TransferClarification) result).voiceMessage())
                .isEqualTo("계좌번호를 말씀해 주세요.");
    }

    @Test
    @DisplayName("인식 신뢰도가 낮으면 검증하지 않고 다시 말하도록 한다")
    void 신뢰도가_낮으면_다시_말하게_한다() {
        // given
        final TransferCommandRequest command = TransferCommandRequest.of(
                50_000L, "엄마", null, LOW_CONFIDENCE, TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE);

        // when & then
        assertThatThrownBy(() -> transferValidationService.validate(USER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOW_CONFIDENCE);
    }

    private void allowAnyAmount() {
        given(transferProperties.minimumAmount()).willReturn(1L);
        given(transferProperties.perTransferLimit()).willReturn(1_000_000L);
    }

    private TransferCommandRequest byName(final Long amount, final String recipient) {
        return TransferCommandRequest.of(
                amount, recipient, null, TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE);
    }

    private TransferCommandRequest byAccount(
            final Long amount,
            final String recipient,
            final String accountNumber,
            final String bankCode
    ) {
        return TransferCommandRequest.of(
                amount, recipient, accountNumber, bankCode, null,
                TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE, TRUSTED_CONFIDENCE);
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

    private TransferRecipient verifiedRecipient(
            final String nickname,
            final String bankCode,
            final String hash
    ) {
        return recipient(nickname, true, bankCode, hash, LocalDateTime.now().minusDays(1));
    }

    private TransferRecipient unverifiedRecipient(
            final String nickname,
            final String bankCode,
            final String hash
    ) {
        return recipient(nickname, true, bankCode, hash, null);
    }

    private TransferRecipient oneTimeRecipient(final String bankCode, final String hash) {
        return recipient(null, false, bankCode, hash, LocalDateTime.now());
    }

    private TransferRecipient recipient(
            final String nickname,
            final boolean addressBook,
            final String bankCode,
            final String hash,
            final LocalDateTime verifiedAt
    ) {
        final TransferRecipient recipient = TransferRecipient.builder()
                .user(user())
                .nickname(nickname)
                .bankCode(bankCode)
                .accountNum("encrypted")
                .accountNumHash(hash)
                .holderName("이영자")
                .addressBook(addressBook)
                .verifiedAt(verifiedAt)
                .build();
        ReflectionTestUtils.setField(recipient, "id", 200L);
        return recipient;
    }
}
