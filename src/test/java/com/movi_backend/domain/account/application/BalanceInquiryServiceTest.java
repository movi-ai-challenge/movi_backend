package com.movi_backend.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceInquiryServiceTest {

    private static final Long USER_ID = 3L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceSnapshotRepository balanceSnapshotRepository;

    @Mock
    private BalanceInquiryPort balanceInquiryPort;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private Account account;

    @Mock
    private OpenbankingConnection connection;

    @Mock
    private User user;

    @InjectMocks
    private BalanceInquiryService balanceInquiryService;

    @Test
    @DisplayName("기본 계좌의 잔액을 조회하면 조회 결과와 음성 안내를 반환한다")
    void 기본_계좌의_잔액을_조회하면_조회_결과와_음성_안내를_반환한다() {
        // given
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(true);
        given(account.getConnection()).willReturn(connection);
        given(connection.getUser()).willReturn(user);
        given(connection.isUsable(any(LocalDateTime.class))).willReturn(true);
        given(account.getFintechUseNum()).willReturn("fintech-use-num");
        given(connection.getAccessToken()).willReturn("encrypted-access-token");
        given(sensitiveDataCrypto.decrypt("encrypted-access-token"))
                .willReturn("plain-access-token");
        given(balanceInquiryPort.inquire("fintech-use-num", "plain-access-token"))
                .willReturn(BalanceInquiryResult.of(53_000L, 50_000L));
        given(balanceSnapshotRepository.save(any(BalanceSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(account.getId()).willReturn(12L);
        given(account.getBankName()).willReturn("국민은행");
        given(account.getAlias()).willReturn("생활비 통장");

        // when
        final BalanceResponse response = balanceInquiryService.inquire(USER_ID, null);

        // then
        assertThat(response.accountId()).isEqualTo(12L);
        assertThat(response.balanceAmount()).isEqualTo(53_000L);
        assertThat(response.availableAmount()).isEqualTo(50_000L);
        assertThat(response.toVoiceMessage()).isEqualTo("국민은행 생활비 통장에 5만 3천원 있어요.");

        final ArgumentCaptor<BalanceSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(BalanceSnapshot.class);
        then(balanceSnapshotRepository).should().save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getAccount()).isEqualTo(account);
        assertThat(snapshotCaptor.getValue().getBalanceAmount()).isEqualTo(53_000L);
        assertThat(snapshotCaptor.getValue().getAvailableAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("계좌 별칭을 지정하면 해당 사용자의 계좌 잔액을 조회한다")
    void 계좌_별칭을_지정하면_해당_사용자의_계좌_잔액을_조회한다() {
        // given
        given(accountRepository.findByUserIdAndAlias(USER_ID, "용돈 통장"))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(true);
        given(account.getConnection()).willReturn(connection);
        given(connection.getUser()).willReturn(user);
        given(connection.isUsable(any(LocalDateTime.class))).willReturn(true);
        given(account.getFintechUseNum()).willReturn("fintech-use-num");
        given(connection.getAccessToken()).willReturn("encrypted-access-token");
        given(sensitiveDataCrypto.decrypt("encrypted-access-token"))
                .willReturn("plain-access-token");
        given(balanceInquiryPort.inquire("fintech-use-num", "plain-access-token"))
                .willReturn(BalanceInquiryResult.of(10_000L, 10_000L));
        given(balanceSnapshotRepository.save(any(BalanceSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(account.getBankName()).willReturn("신한은행");
        given(account.getAlias()).willReturn("용돈 통장");

        // when
        final BalanceResponse response = balanceInquiryService.inquire(USER_ID, "  용돈 통장  ");

        // then
        assertThat(response.balanceAmount()).isEqualTo(10_000L);
        assertThat(response.toVoiceMessage()).isEqualTo("신한은행 용돈 통장에 1만원 있어요.");
    }

    @Test
    @DisplayName("기본 계좌가 없으면 기본 계좌 설정 예외가 발생한다")
    void 기본_계좌가_없으면_기본_계좌_설정_예외가_발생한다() {
        // given
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> balanceInquiryService.inquire(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRIMARY_ACCOUNT_NOT_SET);
    }

    @Test
    @DisplayName("비활성 계좌의 잔액을 조회하면 사용할 수 없는 계좌 예외가 발생한다")
    void 비활성_계좌의_잔액을_조회하면_사용할_수_없는_계좌_예외가_발생한다() {
        // given
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(false);

        // when & then
        assertThatThrownBy(() -> balanceInquiryService.inquire(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        then(balanceInquiryPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 오픈뱅킹 연결로 조회하면 연결 만료 예외가 발생한다")
    void 만료된_오픈뱅킹_연결로_조회하면_연결_만료_예외가_발생한다() {
        // given
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(true);
        given(account.getConnection()).willReturn(connection);
        given(connection.getUser()).willReturn(user);
        given(connection.isUsable(any(LocalDateTime.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> balanceInquiryService.inquire(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONNECTION_EXPIRED);
        then(balanceInquiryPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("오픈뱅킹이 잘못된 잔액을 반환하면 잔액 조회 예외가 발생한다")
    void 오픈뱅킹이_잘못된_잔액을_반환하면_잔액_조회_예외가_발생한다() {
        // given
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(true);
        given(account.getConnection()).willReturn(connection);
        given(connection.getUser()).willReturn(user);
        given(connection.isUsable(any(LocalDateTime.class))).willReturn(true);
        given(account.getFintechUseNum()).willReturn("fintech-use-num");
        given(connection.getAccessToken()).willReturn("encrypted-access-token");
        given(sensitiveDataCrypto.decrypt("encrypted-access-token"))
                .willReturn("plain-access-token");
        given(balanceInquiryPort.inquire("fintech-use-num", "plain-access-token"))
                .willReturn(BalanceInquiryResult.of(1_000L, 2_000L));

        // when & then
        assertThatThrownBy(() -> balanceInquiryService.inquire(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_INQUIRY_FAILED);
        then(balanceSnapshotRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("저장된 액세스 토큰 복호화가 실패하면 오픈뱅킹을 호출하지 않는다")
    void 액세스_토큰_복호화_실패_시_외부_API를_호출하지_않는다() {
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(account.isActive()).willReturn(true);
        given(account.getConnection()).willReturn(connection);
        given(connection.getUser()).willReturn(user);
        given(connection.isUsable(any(LocalDateTime.class))).willReturn(true);
        given(account.getFintechUseNum()).willReturn("fintech-use-num");
        given(connection.getAccessToken()).willReturn("tampered-token");
        given(sensitiveDataCrypto.decrypt("tampered-token"))
                .willThrow(new IllegalStateException("decrypt failed"));

        assertThatThrownBy(() -> balanceInquiryService.inquire(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_INQUIRY_FAILED);
        then(balanceInquiryPort).shouldHaveNoInteractions();
    }
}
