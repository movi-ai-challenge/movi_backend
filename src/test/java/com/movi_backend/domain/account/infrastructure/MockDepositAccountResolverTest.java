package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.account.application.InternalAccountLocator;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.type.AccountType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockDepositAccountResolverTest {

    @Mock
    private InternalAccountLocator internalAccountLocator;

    @InjectMocks
    private MockDepositAccountResolver resolver;

    @Test
    @DisplayName("은행코드와 전체 계좌번호가 정확히 맞으면 받는 계좌를 찾는다")
    void 정확히_맞는_계좌를_찾는다() {
        // given
        given(internalAccountLocator.locate("011", "35211112299"))
                .willReturn(Optional.of(account("fintech-순자")));

        // when
        final Optional<String> found = resolver.resolveFintechUseNum("011", "352-1111-2299");

        // then — 하이픈이 섞여 있어도 숫자만 남겨 조회한다
        assertThat(found).contains("fintech-순자");
    }

    @Test
    @DisplayName("앞자리만 같고 뒷자리가 다른 계좌에는 입금하지 않는다")
    void 앞자리만_같은_계좌에는_입금하지_않는다() {
        // given — 예전에는 앞 여섯 자리만 맞으면 남의 계좌에 입금됐다
        given(internalAccountLocator.locate("011", "35211110000"))
                .willReturn(Optional.empty());

        // when & then
        assertThat(resolver.resolveFintechUseNum("011", "35211110000")).isEmpty();
    }

    @Test
    @DisplayName("우리 사용자가 아닌 계좌로 보내면 입금을 건너뛴다")
    void 우리_사용자가_아니면_건너뛴다() {
        // given
        given(internalAccountLocator.locate("088", "110123456789"))
                .willReturn(Optional.empty());

        // when & then
        assertThat(resolver.resolveFintechUseNum("088", "110123456789")).isEmpty();
    }

    private Account account(final String fintechUseNum) {
        return Account.builder()
                .fintechUseNum(fintechUseNum)
                .bankCode("011")
                .bankName("농협은행")
                .accountNumMasked("352-****-**99")
                .accountType(AccountType.DEPOSIT)
                .build();
    }
}
