package com.movi_backend.domain.transfer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
@Transactional
class TransactionRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("기간과 출금 유형으로 필터링하면 종료일을 포함해 최신 거래부터 반환한다")
    void 기간과_출금_유형으로_필터링하면_종료일을_포함해_최신_거래부터_반환한다() {
        // given
        final User user = userRepository.save(User.builder()
                .name("거래조회 사용자")
                .phone("encrypted-phone-transaction-query")
                .phoneHash("transaction-query-user-hash")
                .userType(UserType.GENERAL)
                .build());
        final Account account = accountRepository.save(Account.builder()
                .user(user)
                .fintechUseNum("transaction-query-fintech-num")
                .bankCode("088")
                .bankName("신한은행")
                .accountNumMasked("encrypted-account")
                .accountType(AccountType.DEPOSIT)
                .build());
        transactionRepository.saveAll(List.of(
                transaction(account, TransactionType.OUT, 10_000L,
                        LocalDateTime.of(2026, 7, 31, 23, 59)),
                transaction(account, TransactionType.OUT, 20_000L,
                        LocalDateTime.of(2026, 8, 24, 10, 0)),
                transaction(account, TransactionType.IN, 30_000L,
                        LocalDateTime.of(2026, 8, 24, 11, 0)),
                transaction(account, TransactionType.OUT, 40_000L,
                        LocalDateTime.of(2026, 8, 24, 12, 0))
        ));

        // when
        final Page<Transaction> result = transactionRepository.findHistory(
                account.getId(),
                TransactionType.OUT,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 25, 0, 0),
                PageRequest.of(
                        0,
                        20,
                        Sort.by(Sort.Order.desc("tranDatetime"), Sort.Order.desc("id"))
                )
        );

        // then
        assertThat(result.getContent())
                .extracting(Transaction::getAmount)
                .containsExactly(40_000L, 20_000L);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    private Transaction transaction(
            final Account account,
            final TransactionType type,
            final long amount,
            final LocalDateTime transactedAt
    ) {
        return Transaction.builder()
                .account(account)
                .tranType(type)
                .amount(amount)
                .balanceAfter(1_000_000L - amount)
                .counterpartyName("거래 상대")
                .tranDatetime(transactedAt)
                .source(TransactionSource.OPENBANKING)
                .build();
    }
}
