package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.Transaction;
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
                new TransactionQueryService(accountRepository, transactionRepository);

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
                new TransactionQueryService(accountRepository, transactionRepository);

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
                new TransactionQueryService(accountRepository, transactionRepository);

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
}
