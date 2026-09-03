package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 오픈뱅킹이 모르는 계좌의 잔액을 대신 들고 있는 장부.
 *
 * <p>연결만 실제로 하고({@code mode=real}) 잔액·이체는 대역을 쓰는 조합에서 필요하다. 실제로
 * 연결된 계좌의 핀테크이용번호는 {@code MockOpenBankingClient}에 없어서, 그 계좌들의 잔액을
 * 여기서 만들어 들고 간다.
 *
 * <p><b>이체하면 잔액이 줄어야 한다.</b> 보내고 나서 잔액을 다시 묻는 흐름이 이 제품의 기본
 * 동선인데, 금액이 그대로면 사용자는 이체가 안 된 것으로 듣는다.
 *
 * <p>처음 보는 계좌의 잔액은 핀테크이용번호에서 만들어 낸다. <b>계좌마다 다르고, 같은 계좌면
 * 언제 물어도 같은 금액</b>이어야 한다. 모두 같으면 계좌를 골라도 구분이 안 되고, 물을 때마다
 * 달라지면 이체 한도·잔액 검증이 통과했다 실패했다 한다. {@code String.hashCode()}는 명세에
 * 값이 고정돼 있어 실행·장비가 달라져도 같은 금액이 나온다.
 *
 * <p><b>받는 쪽 잔액도 늘려 준다.</b> 실제 오픈뱅킹이라면 상대 은행이 입금하지만 대역에는
 * 그 상대가 없다. 보내는 사람과 받는 사람이 둘 다 우리 서비스 사용자인 시연에서는, 한쪽만
 * 줄고 다른 쪽이 그대로면 "돈이 사라진" 것처럼 보인다.
 *
 * <p>메모리에만 있으므로 재기동하면 처음 금액으로 돌아간다. 시연·개발용 대역이다.
 */
@Slf4j
@Component
public class MockTransferLedger {

    private static final long FALLBACK_BALANCE = 530_000L;

    /** 만원 단위로만 만든다. TTS가 "12만원"처럼 읽기 좋고 자잘한 끝자리가 없다. */
    private static final long SEED_UNIT = 10_000L;
    private static final long SEED_MINIMUM = 120_000L;
    private static final int SEED_BUCKETS = 300;

    private final Map<String, AtomicLong> balances = new ConcurrentHashMap<>();
    private final Map<String, OpenBankingTransferResult> executedTransfers = new ConcurrentHashMap<>();

    /**
     * 입금까지 끝낸 거래.
     *
     * <p>출금과 따로 둔다. 출금은 이 장부가 할 수도, {@code MockOpenBankingClient}가 할 수도
     * 있어서 한쪽 기록만 보고 판단하면 재시도 때 입금이 두 번 들어간다.
     */
    private final Map<String, Boolean> depositedTransfers = new ConcurrentHashMap<>();

    private final AtomicLong bankTranSequence = new AtomicLong(1L);

    /** 현재 잔액. 처음 보는 계좌면 이 자리에서 만들어 등록한다. */
    public long balanceOf(final String fintechUseNum) {
        if (fintechUseNum == null || fintechUseNum.isBlank()) {
            return FALLBACK_BALANCE;
        }
        return balanceEntryOf(fintechUseNum).get();
    }

    /**
     * 이체를 기록하고 잔액을 깎는다.
     *
     * <p>같은 {@code tranId}로 다시 들어오면 새로 깎지 않고 기존 결과를 돌려준다. 음성은
     * 중복 발화가 잦고 모바일 네트워크는 재시도가 흔해서, 대역도 멱등해야 실제와 같은 흐름이 된다.
     */
    public OpenBankingTransferResult transfer(final OpenBankingTransferCommand command) {
        final OpenBankingTransferResult executed = executedTransfers.get(command.tranId());
        if (executed != null) {
            log.info("[MOCK-LEDGER] 중복 이체 요청 — 기존 결과 반환 tranId={}", command.tranId());
            return executed;
        }

        final long remaining = withdraw(
                balanceEntryOf(command.fromFintechUseNum()),
                command.amount()
        );
        final OpenBankingTransferResult result = OpenBankingTransferResult.of(
                "MOCKLEDGER%08d".formatted(bankTranSequence.getAndIncrement()),
                LocalDateTime.now(),
                remaining
        );
        executedTransfers.put(command.tranId(), result);
        log.info("[MOCK-LEDGER] 이체 완료 tranId={} bankTranId={}", command.tranId(), result.bankTranId());
        return result;
    }

    /**
     * 받는 계좌에 입금한다. 같은 {@code tranId}로 다시 들어오면 더 넣지 않는다.
     *
     * <p>받는 계좌를 못 찾았거나 우리 사용자가 아니면 부르지 않는다 — 이 대역이 잔액을
     * 들고 있는 계좌만 대상이다.
     */
    public void deposit(
            final String tranId,
            final String toFintechUseNum,
            final long amount
    ) {
        if (depositedTransfers.putIfAbsent(tranId, Boolean.TRUE) != null) {
            log.info("[MOCK-LEDGER] 중복 입금 요청 - 건너뜁니다 tranId={}", tranId);
            return;
        }
        final long after = balanceEntryOf(toFintechUseNum).addAndGet(amount);
        log.info("[MOCK-LEDGER] 입금 완료 tranId={} 잔액={}", tranId, after);
    }

    private AtomicLong balanceEntryOf(final String fintechUseNum) {
        if (fintechUseNum == null || fintechUseNum.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FINTECH_USE_NUM, "fintechUseNum 없음");
        }
        return balances.computeIfAbsent(
                fintechUseNum,
                key -> new AtomicLong(seedBalanceOf(key))
        );
    }

    private long seedBalanceOf(final String fintechUseNum) {
        final int bucket = Math.floorMod(fintechUseNum.hashCode(), SEED_BUCKETS);
        return SEED_MINIMUM + bucket * SEED_UNIT;
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
