package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.application.RegisteredAccountFinder;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferRecipientCommandServiceTest {

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private RegisteredAccountFinder registeredAccountFinder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private TransferRecipientCommandService transferRecipientCommandService;

    @Test
    @DisplayName("이름이 달라도 이미 등록된 계좌는 다시 등록할 수 없다")
    void 이름이_달라도_같은_계좌는_다시_등록할_수_없다() {
        // given — "엄마"로 등록해 둔 계좌를 "어머니"라는 새 이름으로 또 등록하려는 상황
        given(sensitiveDataCrypto.hash("11122233344")).willReturn("account-hash");
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "어머니"))
                .willReturn(false);
        given(transferRecipientRepository.existsByUserIdAndAccountNumHash(1L, "account-hash"))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> transferRecipientCommandService.register(
                1L, new RecipientRegisterRequest("어머니", "111-2223-3344")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED);
        then(registeredAccountFinder).shouldHaveNoInteractions();
        then(transferRecipientRepository).should(Mockito.never())
                .save(ArgumentMatchers.any(TransferRecipient.class));
    }

    @Test
    @DisplayName("처음 등록하는 계좌는 검색 해시를 함께 저장한다")
    void 처음_등록하는_계좌는_검색_해시를_함께_저장한다() {
        // given
        final User owner = user(2L, "받는 사람");
        final Account account = account(owner, "090", "11122233344");
        final User registrant = user(1L, "등록하는 사람");
        given(sensitiveDataCrypto.hash("11122233344")).willReturn("account-hash");
        given(transferRecipientRepository.existsByUserIdAndNickname(1L, "엄마"))
                .willReturn(false);
        given(transferRecipientRepository.existsByUserIdAndAccountNumHash(1L, "account-hash"))
                .willReturn(false);
        given(registeredAccountFinder.findByAccountNumber("11122233344")).willReturn(account);
        given(userRepository.findById(1L)).willReturn(Optional.of(registrant));
        given(sensitiveDataCrypto.encrypt("11122233344")).willReturn("encrypted-account-num");
        given(transferRecipientRepository.save(ArgumentMatchers.any(TransferRecipient.class)))
                .willAnswer(invocation -> {
                    final TransferRecipient saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 10L);
                    return saved;
                });

        // when
        final RecipientResponse response = transferRecipientCommandService.register(
                1L, new RecipientRegisterRequest("엄마", "111-2223-3344")
        );

        // then
        assertThat(response.nickname()).isEqualTo("엄마");
        final ArgumentCaptor<TransferRecipient> captor = ArgumentCaptor.forClass(TransferRecipient.class);
        then(transferRecipientRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAccountNumHash()).isEqualTo("account-hash");
    }

    private User user(final Long userId, final String name) {
        final User user = User.builder()
                .name(name)
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Account account(final User owner, final String bankCode, final String accountNumMasked) {
        return Account.builder()
                .user(owner)
                .bankCode(bankCode)
                .bankName("테스트은행")
                .accountNumMasked(accountNumMasked)
                .accountType(AccountType.DEPOSIT)
                .build();
    }
}
