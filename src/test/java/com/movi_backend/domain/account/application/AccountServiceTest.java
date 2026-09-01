package com.movi_backend.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.dto.response.AccountListResponse;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ACCOUNT_ID = 12L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("계좌 별칭의 앞뒤 공백을 제거해 변경한다")
    void 계좌_별칭의_앞뒤_공백을_제거해_변경한다() {
        final Account account = account("기존 별칭");
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(account));
        given(accountRepository.findByUserIdAndAlias(USER_ID, "월급통장"))
                .willReturn(Optional.empty());

        final AccountResponse response = accountService.changeAlias(
                USER_ID,
                ACCOUNT_ID,
                "\u2003월급통장\u2003"
        );

        assertThat(account.getAlias()).isEqualTo("월급통장");
        assertThat(response.accountAlias()).isEqualTo("월급통장");
    }

    @Test
    @DisplayName("동시 변경으로 별칭 UNIQUE 제약이 충돌하면 중복 별칭 예외가 발생한다")
    void 동시_변경으로_별칭_UNIQUE_제약이_충돌하면_중복_별칭_예외가_발생한다() {
        final Account account = account("기존 별칭");
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(account));
        given(accountRepository.findByUserIdAndAlias(USER_ID, "월급통장"))
                .willReturn(Optional.empty());
        org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("unique constraint"))
                .given(accountRepository)
                .flush();

        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "월급통장"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALIAS_DUPLICATED);
    }

    @Test
    @DisplayName("같은 사용자의 다른 계좌가 별칭을 사용 중이면 변경할 수 없다")
    void 같은_사용자의_다른_계좌가_별칭을_사용_중이면_변경할_수_없다() {
        final Account target = account("기존 별칭");
        final Account duplicated = account("월급통장");
        ReflectionTestUtils.setField(duplicated, "id", 13L);
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(target));
        given(accountRepository.findByUserIdAndAlias(USER_ID, "월급통장"))
                .willReturn(Optional.of(duplicated));

        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "월급통장"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_ALIAS_DUPLICATED);
        assertThat(target.getAlias()).isEqualTo("기존 별칭");
    }

    @Test
    @DisplayName("비활성 계좌의 별칭은 변경할 수 없다")
    void 비활성_계좌의_별칭은_변경할_수_없다() {
        final Account account = account("기존 별칭");
        account.deactivate();
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "월급통장"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        then(accountRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("빈 별칭은 변경할 수 없다")
    void 빈_별칭은_변경할_수_없다() {
        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(accountRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("50자를 넘는 별칭은 변경할 수 없다")
    void 오십_자를_넘는_별칭은_변경할_수_없다() {
        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "가".repeat(51)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(accountRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사용자 소유가 아닌 계좌의 별칭은 변경할 수 없다")
    void 사용자_소유가_아닌_계좌의_별칭은_변경할_수_없다() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changeAlias(USER_ID, ACCOUNT_ID, "월급통장"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("계좌 연결을 해제하면 행을 지우지 않고 비활성으로 내린다")
    void 계좌_연결을_해제하면_행을_지우지_않고_비활성으로_내린다() {
        final Account target = account("생활비 통장");
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(target));
        given(transferRepository.existsByFromAccountIdAndStatusIn(anyLong(), any())).willReturn(false);
        given(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(USER_ID))
                .willReturn(List.of());

        final AccountListResponse remaining = accountService.disconnect(USER_ID, ACCOUNT_ID);

        assertThat(target.isActive()).isFalse();
        assertThat(remaining.totalCount()).isZero();
        then(accountRepository).should(org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("기본 계좌를 해제하면 남은 계좌 중 하나가 기본 계좌가 된다")
    void 기본_계좌를_해제하면_남은_계좌_중_하나가_기본_계좌가_된다() {
        final Account target = account("생활비 통장");
        target.designateAsPrimary();
        final Account survivor = accountWithId(77L, "비상금 통장");
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(target));
        given(transferRepository.existsByFromAccountIdAndStatusIn(anyLong(), any())).willReturn(false);
        given(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(USER_ID))
                .willReturn(List.of(survivor));

        accountService.disconnect(USER_ID, ACCOUNT_ID);

        assertThat(target.isPrimary()).isFalse();
        assertThat(survivor.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("기본이 아닌 계좌를 해제하면 기존 기본 계좌는 그대로 둔다")
    void 기본이_아닌_계좌를_해제하면_기존_기본_계좌는_그대로_둔다() {
        final Account target = account("생활비 통장");
        final Account primary = accountWithId(77L, "월급 통장");
        primary.designateAsPrimary();
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(target));
        given(transferRepository.existsByFromAccountIdAndStatusIn(anyLong(), any())).willReturn(false);
        given(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(USER_ID))
                .willReturn(List.of(primary));

        accountService.disconnect(USER_ID, ACCOUNT_ID);

        assertThat(primary.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("보내는 중인 이체가 걸린 계좌는 해제할 수 없다")
    void 보내는_중인_이체가_걸린_계좌는_해제할_수_없다() {
        final Account target = account("생활비 통장");
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(target));
        given(transferRepository.existsByFromAccountIdAndStatusIn(
                ACCOUNT_ID,
                java.util.Set.of(TransferStatus.PENDING, TransferStatus.RISK_REVIEW)
        )).willReturn(true);

        assertThatThrownBy(() -> accountService.disconnect(USER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_HAS_PENDING_TRANSFER);
        assertThat(target.isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 해제한 계좌는 다시 해제할 수 없다")
    void 이미_해제한_계좌는_다시_해제할_수_없다() {
        final Account target = account("생활비 통장");
        target.deactivate();
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> accountService.disconnect(USER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    @DisplayName("사용자 소유가 아닌 계좌는 해제할 수 없다")
    void 사용자_소유가_아닌_계좌는_해제할_수_없다() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.disconnect(USER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_FOUND);
    }

    private Account accountWithId(final Long id, final String alias) {
        final Account account = Account.builder()
                .user(org.mockito.Mockito.mock(User.class))
                .fintechUseNum("fintech-use-num-" + id)
                .bankCode("088")
                .bankName("신한은행")
                .accountNumMasked("110-***-987654")
                .alias(alias)
                .accountType(AccountType.DEPOSIT)
                .build();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private Account account(final String alias) {
        final Account account = Account.builder()
                .user(org.mockito.Mockito.mock(User.class))
                .fintechUseNum("fintech-use-num")
                .bankCode("004")
                .bankName("국민은행")
                .accountNumMasked("123-***-456789")
                .alias(alias)
                .accountType(AccountType.DEPOSIT)
                .build();
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        return account;
    }
}
