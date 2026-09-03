package com.movi_backend.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisteredAccountFinder 는")
class RegisteredAccountFinderTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private RegisteredAccountFinder registeredAccountFinder;

    private Account account(final String masked, final boolean active) {
        final Account account = Account.builder().build();
        ReflectionTestUtils.setField(account, "accountNumMasked", masked);
        ReflectionTestUtils.setField(account, "active", active);
        return account;
    }

    @Test
    @DisplayName("가려지기 전 앞자리가 맞으면 그 계좌를 찾는다")
    void findsByVisiblePrefix() {
        final Account target = account("123456-**-*****1", true);
        given(accountRepository.findAll()).willReturn(List.of(target));

        assertThat(registeredAccountFinder.findByAccountNumber("123456789012")).isSameAs(target);
    }

    @Test
    @DisplayName("하이픈과 공백이 섞여 있어도 같은 계좌를 찾는다")
    void ignoresSeparators() {
        final Account target = account("123456-**-*****1", true);
        given(accountRepository.findAll()).willReturn(List.of(target));

        assertThat(registeredAccountFinder.findByAccountNumber(" 123456-78 9012 "))
                .isSameAs(target);
    }

    @Test
    @DisplayName("연결이 끊긴 계좌는 후보로 보지 않는다")
    void ignoresInactiveAccount() {
        given(accountRepository.findAll()).willReturn(List.of(account("123456-**-*****1", false)));

        assertThatThrownBy(() -> registeredAccountFinder.findByAccountNumber("123456789012"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("맞는 계좌가 없으면 등록을 거절한다")
    void rejectsUnknownAccount() {
        given(accountRepository.findAll()).willReturn(List.of(account("999999-**-*****1", true)));

        assertThatThrownBy(() -> registeredAccountFinder.findByAccountNumber("123456789012"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("후보가 둘이면 엉뚱한 사람에게 갈 수 있으므로 거절한다")
    void rejectsAmbiguousAccount() {
        given(accountRepository.findAll()).willReturn(List.of(
                account("123456-**-*****1", true),
                account("123456-**-*****9", true)
        ));

        assertThatThrownBy(() -> registeredAccountFinder.findByAccountNumber("123456789012"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECIPIENT_ACCOUNT_AMBIGUOUS);
    }

    @Test
    @DisplayName("너무 짧은 번호는 남의 계좌를 잡을 수 있어 찾지 않는다")
    void rejectsTooShortNumber() {
        assertThatThrownBy(() -> registeredAccountFinder.findByAccountNumber("12345"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
    }
}
