package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.type.AccountType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockDepositAccountResolverTest {

    @Mock
    private AccountRepository accountRepository;

    private MockDepositAccountResolver resolver() {
        return new MockDepositAccountResolver(accountRepository);
    }

    private Account account(
            final String fintechUseNum,
            final String bankCode,
            final String maskedAccountNumber
    ) {
        return Account.builder()
                .fintechUseNum(fintechUseNum)
                .bankCode(bankCode)
                .bankName("테스트은행")
                .accountNumMasked(maskedAccountNumber)
                .accountType(AccountType.DEPOSIT)
                .build();
    }

    @Test
    @DisplayName("은행코드와 노출된 앞자리가 맞으면 받는 계좌를 찾는다")
    void 받는_계좌를_찾는다() {
        // given - accounts 에는 마스킹된 값만 있고 이체 명령은 실제 번호를 들고 온다.
        given(accountRepository.findAll())
                .willReturn(List.of(account("fintech-주혁", "012", "3522315749***")));

        // when
        final Optional<String> found =
                resolver().resolveFintechUseNum("012", "3522315749001");

        // then
        assertThat(found).contains("fintech-주혁");
    }

    @Test
    @DisplayName("은행이 다르면 찾지 않는다")
    void 은행이_다르면_찾지_않는다() {
        given(accountRepository.findAll())
                .willReturn(List.of(account("fintech-주혁", "012", "3522315749***")));

        assertThat(resolver().resolveFintechUseNum("004", "3522315749001")).isEmpty();
    }

    @Test
    @DisplayName("후보가 둘이면 입금하지 않는다 - 엉뚱한 사람에게 돈이 들어가면 안 된다")
    void 후보가_둘이면_입금하지_않는다() {
        given(accountRepository.findAll()).willReturn(List.of(
                account("fintech-1", "012", "3522315749***"),
                account("fintech-2", "012", "3522315749***")
        ));

        assertThat(resolver().resolveFintechUseNum("012", "3522315749001")).isEmpty();
    }

    @Test
    @DisplayName("노출된 앞자리가 너무 짧으면 찾지 않는다")
    void 앞자리가_짧으면_찾지_않는다() {
        given(accountRepository.findAll())
                .willReturn(List.of(account("fintech-주혁", "012", "352**********")));

        assertThat(resolver().resolveFintechUseNum("012", "3522315749001")).isEmpty();
    }

    @Test
    @DisplayName("우리 사용자가 아닌 계좌로 보내면 빈 값을 준다")
    void 우리_사용자가_아니면_빈_값을_준다() {
        given(accountRepository.findAll())
                .willReturn(List.of(account("fintech-주혁", "012", "3522315749***")));

        assertThat(resolver().resolveFintechUseNum("088", "1109876543210")).isEmpty();
    }
}
