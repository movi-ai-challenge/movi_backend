package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.transfer.dto.response.TransactionDetailResponse;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.Transaction;
import org.springframework.test.util.ReflectionTestUtils;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ACCOUNT_ID = 10L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private FdsAssessmentRepository fdsAssessmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private Account account;

    @Test
    @DisplayName("기본 계좌의 거래내역을 기간과 유형으로 최신순 조회한다")
    void 기본_계좌의_거래내역을_기간과_유형으로_최신순_조회한다() {
        // given
        final LocalDate startDate = LocalDate.of(2026, 8, 1);
        final LocalDate endDate = LocalDate.of(2026, 8, 24);
        final Transaction transaction = transaction();
        given(accountRepository.findByUserIdAndPrimaryTrue(USER_ID))
                .willReturn(Optional.of(account));
        given(account.isActive()).willReturn(true);
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(transactionRepository.findHistory(
                org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                org.mockito.ArgumentMatchers.eq(TransactionType.OUT),
                org.mockito.ArgumentMatchers.eq(startDate.atStartOfDay()),
                org.mockito.ArgumentMatchers.eq(endDate.plusDays(1).atStartOfDay()),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(transaction)));
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // when
        final PageResponse<TransactionResponse> result = service.findAll(
                USER_ID,
                null,
                startDate,
                endDate,
                TransactionType.OUT,
                0,
                20
        );

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.transactionId()).isEqualTo(101L);
            assertThat(item.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(item.type()).isEqualTo(TransactionType.OUT);
            assertThat(item.amount()).isEqualTo(50_000L);
            assertThat(item.counterpartyName()).isEqualTo("김영희");
        });
        then(transactionRepository).should().findHistory(
                org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
                org.mockito.ArgumentMatchers.eq(TransactionType.OUT),
                org.mockito.ArgumentMatchers.eq(startDate.atStartOfDay()),
                org.mockito.ArgumentMatchers.eq(endDate.plusDays(1).atStartOfDay()),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 20
                        && pageable.getSort().getOrderFor("tranDatetime").isDescending()
                        && pageable.getSort().getOrderFor("id").isDescending())
        );
    }

    @Test
    @DisplayName("다른 사용자의 계좌를 지정하면 계좌 없음으로 차단한다")
    void 다른_사용자의_계좌를_지정하면_계좌_없음으로_차단한다() {
        // given
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.empty());
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // when & then
        assertThatThrownBy(() -> service.findAll(
                USER_ID,
                ACCOUNT_ID,
                null,
                null,
                null,
                0,
                20
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        then(transactionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 잘못된 요청으로 거부한다")
    void 시작일이_종료일보다_늦으면_잘못된_요청으로_거부한다() {
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        assertThatThrownBy(() -> service.findAll(
                USER_ID,
                null,
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 24),
                null,
                0,
                20
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(accountRepository).shouldHaveNoInteractions();
        then(transactionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("본인 거래 1건을 상세 조회하면 잔액과 메모까지 반환한다")
    void 본인_거래_1건을_상세_조회하면_잔액과_메모까지_반환한다() {
        // given
        final Transaction transaction = transaction();
        final User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(account.getUser()).willReturn(user);
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(transactionRepository.findById(101L)).willReturn(Optional.of(transaction));
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // when
        final TransactionDetailResponse result = service.findOne(USER_ID, 101L);

        // then
        assertThat(result.transactionId()).isEqualTo(101L);
        assertThat(result.amount()).isEqualTo(50_000L);
        assertThat(result.balanceAfter()).isEqualTo(950_000L);
        assertThat(result.counterpartyName()).isEqualTo("김영희");
        assertThat(result.memo()).isEqualTo("용돈");
    }

    @Test
    @DisplayName("상세 음성 안내는 금액과 잔액을 한국어로 읽는다")
    void 상세_음성_안내는_금액과_잔액을_한국어로_읽는다() {
        // given
        final Transaction transaction = transaction();
        final User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(account.getUser()).willReturn(user);
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(transactionRepository.findById(101L)).willReturn(Optional.of(transaction));
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // when
        final String voiceMessage = service.findOne(USER_ID, 101L).toVoiceMessage();

        // then
        assertThat(voiceMessage)
                .contains("8월 24일")
                .contains("김영희 님에게 5만원 보냈어요")
                .contains("거래 뒤 잔액은 95만원이에요")
                .contains("메모는 용돈이에요");
        assertThat(voiceMessage).doesNotContain("50000");
        assertThat(voiceMessage).doesNotContain("950000");
    }

    @Test
    @DisplayName("다른 사용자의 거래는 상세 조회할 수 없다")
    void 다른_사용자의_거래는_상세_조회할_수_없다() {
        // given — 소유권에서 걸러지므로 거래 상세 값은 읽히지 않는다
        final Transaction transaction = mock(Transaction.class);
        final User otherUser = mock(User.class);
        given(otherUser.getId()).willReturn(99L);
        given(account.getUser()).willReturn(otherUser);
        given(transaction.getAccount()).willReturn(account);
        given(transactionRepository.findById(101L)).willReturn(Optional.of(transaction));
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // expect
        assertThatThrownBy(() -> service.findOne(USER_ID, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 거래를 상세 조회하면 거래내역 없음으로 응답한다")
    void 존재하지_않는_거래를_상세_조회하면_거래내역_없음으로_응답한다() {
        // given
        given(transactionRepository.findById(404L)).willReturn(Optional.empty());
        final TransactionQueryService service =
                new TransactionQueryService(
                        accountRepository,
                        fdsAssessmentRepository,
                        transactionRepository
                );

        // expect
        assertThatThrownBy(() -> service.findOne(USER_ID, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
    }

    private Transaction transaction() {
        final Transaction transaction = mock(Transaction.class);
        given(transaction.getId()).willReturn(101L);
        given(transaction.getAccount()).willReturn(account);
        given(transaction.getTranType()).willReturn(TransactionType.OUT);
        given(transaction.getAmount()).willReturn(50_000L);
        given(transaction.getBalanceAfter()).willReturn(950_000L);
        given(transaction.getCounterpartyName()).willReturn("김영희");
        given(transaction.getCategory()).willReturn("송금");
        given(transaction.getTranDatetime()).willReturn(LocalDateTime.of(2026, 8, 24, 10, 30));
        given(transaction.getMemo()).willReturn("용돈");
        given(transaction.getSource()).willReturn(TransactionSource.INTERNAL);
        return transaction;
    }

    @Test
    @DisplayName("이체를 거친 거래에는 FDS 판정을 함께 내린다")
    void 이체_거래에는_FDS_판정을_붙인다() {
        // given — 거래내역에서 "이 거래가 위험하다고 잡혔다"를 보여 주기 위한 값이다.
        final Transfer transfer = transfer(77L);
        final Transaction transaction = transactionWithTransfer(account, transfer);
        given(account.isActive()).willReturn(true);
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(account));
        given(transactionRepository.findHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(transaction)));
        given(fdsAssessmentRepository.findByTransferIdIn(List.of(77L)))
                .willReturn(List.of(assessment(transfer, RiskLevel.MEDIUM)));
        final TransactionQueryService service = new TransactionQueryService(
                accountRepository,
                fdsAssessmentRepository,
                transactionRepository
        );

        // when
        final PageResponse<TransactionResponse> result =
                service.findAll(USER_ID, ACCOUNT_ID, null, null, null, 0, 20);

        // then
        assertThat(result.content().getFirst().riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("이체를 거치지 않은 거래는 평가를 조회하지 않는다")
    void 외부_거래는_평가를_조회하지_않는다() {
        // 은행에서 내려받은 입출금은 우리 평가가 없다. 그런데도 조회하면
        // 목록을 열 때마다 쓸모없는 질의가 나간다.
        given(account.isActive()).willReturn(true);
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(account));
        given(transactionRepository.findHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(transactionWithTransfer(account, null))));
        final TransactionQueryService service = new TransactionQueryService(
                accountRepository,
                fdsAssessmentRepository,
                transactionRepository
        );

        final PageResponse<TransactionResponse> result =
                service.findAll(USER_ID, ACCOUNT_ID, null, null, null, 0, 20);

        assertThat(result.content().getFirst().riskLevel()).isNull();
        then(fdsAssessmentRepository).shouldHaveNoInteractions();
    }

    private Transfer transfer(final Long id) {
        final Transfer transfer = Transfer.builder().build();
        ReflectionTestUtils.setField(transfer, "id", id);
        return transfer;
    }

    private FdsAssessment assessment(final Transfer transfer, final RiskLevel riskLevel) {
        final FdsAssessment assessment = FdsAssessment.builder()
                .transfer(transfer)
                .riskLevel(riskLevel)
                .build();
        return assessment;
    }

    private Transaction transactionWithTransfer(
            final Account account,
            final Transfer transfer
    ) {
        final Transaction transaction = Transaction.builder()
                .account(account)
                .transfer(transfer)
                .tranType(TransactionType.OUT)
                .amount(10_000L)
                .tranDatetime(LocalDateTime.now())
                .source(TransactionSource.INTERNAL)
                .build();
        ReflectionTestUtils.setField(transaction, "id", 1L);
        return transaction;
    }
}
