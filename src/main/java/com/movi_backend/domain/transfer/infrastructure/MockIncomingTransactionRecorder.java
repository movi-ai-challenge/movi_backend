package com.movi_backend.domain.transfer.infrastructure;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.infrastructure.MockDepositAccountResolver;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 받는 쪽에 입금 거래내역을 남긴다.
 *
 * <p>실제 오픈뱅킹이라면 받는 사람의 거래내역은 그쪽 은행이 기록하고 우리는 조회해서 가져온다.
 * 대역에는 그 은행이 없어서, 보낸 사람 쪽 출금 한 건만 남고 받은 사람 화면에는 아무것도
 * 뜨지 않았다. 두 사람이 각자 폰으로 보는 시연에서 <b>잔액은 늘었는데 내역이 없는</b> 상태가
 * 된다.
 *
 * <p>{@code transfer-mode=mock} 일 때만 등록한다. 실제 이체를 쓰게 되면 받는 쪽 내역을 우리가
 * 만들어서는 안 된다 — 그 순간 같은 거래가 두 번 보이게 된다.
 *
 * <p>받는 사람이 우리 사용자가 아니면 아무것도 하지 않는다. 남길 계좌가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "movi.openbanking.transfer-mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockIncomingTransactionRecorder {

    private final MockDepositAccountResolver depositAccountResolver;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * 입금 내역을 남긴다.
     *
     * <p>이체는 이미 끝났다. 여기서 실패해도 되돌리지 않는다 — 정상적으로 나간 돈이 실패로
     * 보고되면 사용자가 다시 보내 두 번 나간다.
     */
    public void record(
            final String toBankCode,
            final String toAccountNum,
            final String senderName,
            final long amount,
            final LocalDateTime occurredAt
    ) {
        try {
            final Optional<String> fintechUseNum =
                    depositAccountResolver.resolveFintechUseNum(toBankCode, toAccountNum);
            if (fintechUseNum.isEmpty()) {
                return;
            }
            final Optional<Account> account =
                    accountRepository.findByFintechUseNum(fintechUseNum.get());
            if (account.isEmpty()) {
                return;
            }
            transactionRepository.save(Transaction.builder()
                    .account(account.get())
                    .tranType(TransactionType.IN)
                    .amount(amount)
                    .counterpartyName(senderName)
                    .tranDatetime(occurredAt)
                    .source(TransactionSource.INTERNAL)
                    .build());
            log.info("[MOCK] 받는 쪽 입금 내역을 남겼습니다. accountId={}", account.get().getId());
        } catch (final RuntimeException exception) {
            log.warn(
                    "[MOCK] 받는 쪽 입금 내역을 남기지 못했습니다. 이체는 이미 끝났습니다. 원인={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
