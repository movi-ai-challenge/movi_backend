package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 Mock 어댑터.
 *
 * <p>Sandbox 승인 전까지 이 구현으로 개발한다. 승인 후에는 설정만 바꿔 HTTP 어댑터로 교체하며
 * 서비스 코드는 그대로 둔다.
 *
 * <p><b>잔액을 상태로 들고 이체할 때 차감한다.</b> 고정값을 쓰면 잔액 부족 분기를 시연할 수
 * 없고, 연속 이체 시나리오도 어색해지기 때문이다. 잔액 <i>조회</i>는 이 어댑터가 아니라
 * {@code BalanceInquiryPort} 구현체가 담당한다.
 *
 * <p>{@code tranId}가 같은 요청은 새 이체를 만들지 않고 이전 결과를 그대로 돌려준다.
 * 실제 오픈뱅킹의 멱등 동작을 흉내 내, 재시도 로직을 Mock 단계에서 검증할 수 있게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "mock", matchIfMissing = true)
public class MockOpenBankingClient implements OpenBankingClient {

    private static final String PRIMARY_FINTECH_NUM = "199000000000000000000001";
    private static final String SAVING_FINTECH_NUM = "199000000000000000000002";

    private static final List<OpenBankingAccount> ACCOUNTS = List.of(
            OpenBankingAccount.of(PRIMARY_FINTECH_NUM, "004", "국민은행",
                    "123456-**-*****1", "김철수", AccountType.DEPOSIT),
            OpenBankingAccount.of(SAVING_FINTECH_NUM, "088", "신한은행",
                    "110-***-****22", "김철수", AccountType.SAVING)
    );

    /** 핀테크이용번호별 잔액. 이체 시 차감된다 */
    private final Map<String, AtomicLong> balances = new ConcurrentHashMap<>(Map.of(
            PRIMARY_FINTECH_NUM, new AtomicLong(530_000L),
            SAVING_FINTECH_NUM, new AtomicLong(1_200_000L)
    ));

    /** 멱등 키별 이체 결과 */
    private final Map<String, OpenBankingTransferResult> executedTransfers = new ConcurrentHashMap<>();

    private final AtomicLong bankTranSequence = new AtomicLong(1L);

    @Override
    public List<OpenBankingAccount> fetchAccounts(final String userSeqNo) {
        log.info("[MOCK] 계좌 목록 조회 userSeqNo={}", userSeqNo);
        return ACCOUNTS;
    }

    @Override
    public OpenBankingTransferResult transfer(final OpenBankingTransferCommand command) {
        final OpenBankingTransferResult executed = executedTransfers.get(command.tranId());
        if (executed != null) {
            log.info("[MOCK] 중복 이체 요청 — 기존 결과 반환 tranId={}", command.tranId());
            return executed;
        }

        final AtomicLong balance = findBalance(command.fromFintechUseNum());
        final long remaining = withdraw(balance, command.amount());

        final OpenBankingTransferResult result = OpenBankingTransferResult.of(
                "MOCK%08d".formatted(bankTranSequence.getAndIncrement()),
                LocalDateTime.now(),
                remaining
        );
        executedTransfers.put(command.tranId(), result);
        log.info("[MOCK] 이체 완료 tranId={} bankTranId={}", command.tranId(), result.bankTranId());
        return result;
    }

    /**
     * Mock 내부 잔액. 잔액조회 Port가 아니라 <b>테스트·시연 검증용</b>이다.
     * 실제 잔액조회는 {@code BalanceInquiryPort}가 담당한다.
     */
    public long currentBalanceOf(final String fintechUseNum) {
        return findBalance(fintechUseNum).get();
    }

    private AtomicLong findBalance(final String fintechUseNum) {
        final AtomicLong balance = balances.get(fintechUseNum);
        if (balance == null) {
            throw new BusinessException(ErrorCode.INVALID_FINTECH_USE_NUM, "unknown fintechUseNum");
        }
        return balance;
    }

    /** 잔액이 모자라면 차감하지 않고 거부한다. 동시 요청에도 잔액이 음수가 되지 않는다. */
    private long withdraw(final AtomicLong balance, final Long amount) {
        while (true) {
            final long current = balance.get();
            if (current < amount) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            final long next = current - amount;
            if (balance.compareAndSet(current, next)) {
                return next;
            }
        }
    }
}
