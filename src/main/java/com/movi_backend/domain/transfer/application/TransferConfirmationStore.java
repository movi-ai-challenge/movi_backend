package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.application.model.TransferConfirmation;
import com.movi_backend.domain.transfer.config.TransferProperties;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 화면 검토를 마친 송금 스냅샷 저장소.
 *
 * <p>음성 흐름은 같은 역할을 {@code voice_sessions.pending_slots}가 한다. 직접 입력에는
 * 음성 세션이 없으므로 별도로 둔다.
 *
 * <p><b>왜 서버가 들고 있어야 하나.</b> 확인 내용을 프런트가 들고 있다가 실행 요청에 다시
 * 실어 보내면, 검토 화면에 보여 준 금액과 실제로 나가는 금액이 달라질 수 있다. 서버가
 * 스냅샷을 소유하면 실행 요청은 "그 확인을 실행한다"는 뜻만 갖는다.
 *
 * <p>{@link #bindIdempotencyKey}는 확인 하나를 키 하나에 묶는다. 사용자가 실행 버튼을
 * 두 번 눌러 서로 다른 키가 오면 두 번째는 거부된다 — 같은 검토 내용으로 두 건이 나가는
 * 것을 막는다. 같은 키의 재시도(네트워크 타임아웃 복구)는 통과시키고, 실제 중복 방지는
 * {@code TransferExecutionService}의 잠금과 UNIQUE 제약이 마무리한다.
 *
 * <p>MVP 범위라 메모리에 둔다. {@code OAuthStateStore}, {@code LoginHandoffStore}와 같은
 * 이유다 — 서버가 여러 대가 되면 검토를 받은 서버와 실행을 받는 서버가 달라져 실패하므로,
 * 그 시점에는 Redis 같은 공유 저장소로 옮겨야 한다. 재기동 시 진행 중인 확인은 사라지고
 * 사용자는 검토를 다시 하게 되는데, 돈이 나간 뒤가 아니므로 안전한 쪽으로 실패한다.
 */
@Component
@RequiredArgsConstructor
public class TransferConfirmationStore {

    private final Map<String, TransferConfirmation> confirmations = new ConcurrentHashMap<>();

    private final TransferProperties transferProperties;

    /** 검토를 통과한 송금 내용을 저장하고 확인 ID를 발급한다. */
    public TransferConfirmation issue(
            final Long userId,
            final Long fromAccountId,
            final Long recipientId,
            final long amount,
            final LocalDateTime now
    ) {
        evictExpired(now);
        evictPrevious(userId);
        final TransferConfirmation confirmation = TransferConfirmation.of(
                UUID.randomUUID().toString(),
                userId,
                fromAccountId,
                recipientId,
                amount,
                now.plusMinutes(transferProperties.confirmationExpireMinutes())
        );
        confirmations.put(confirmation.confirmationId(), confirmation);
        return confirmation;
    }

    /**
     * 확인을 멱등성 키에 묶고 돌려준다.
     *
     * <p>없거나 만료됐거나 다른 사용자의 확인이거나 이미 다른 키로 실행된 확인이면
     * {@code null}이다. 호출자는 이 경우 이체를 실행하지 않는다.
     */
    public TransferConfirmation bindIdempotencyKey(
            final String confirmationId,
            final Long userId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        if (confirmationId == null || confirmationId.isBlank()) {
            return null;
        }
        // computeIfPresent 는 거부한 확인도 그대로 돌려주므로 성공 여부를 따로 담는다.
        final AtomicReference<TransferConfirmation> bound = new AtomicReference<>();
        confirmations.computeIfPresent(
                confirmationId.trim(),
                (key, confirmation) -> {
                    if (!isBindable(confirmation, userId, idempotencyKey, now)) {
                        return confirmation;
                    }
                    final TransferConfirmation boundConfirmation =
                            confirmation.bindIdempotencyKey(idempotencyKey);
                    bound.set(boundConfirmation);
                    return boundConfirmation;
                }
        );
        return bound.get();
    }

    /** 최종 상태에 도달한 확인을 제거한다. 재실행 시도가 스냅샷을 다시 쓰지 못하게 한다. */
    public void remove(final String confirmationId) {
        if (confirmationId == null) {
            return;
        }
        confirmations.remove(confirmationId);
    }

    private boolean isBindable(
            final TransferConfirmation confirmation,
            final Long userId,
            final String idempotencyKey,
            final LocalDateTime now
    ) {
        if (confirmation.isExpired(now)) {
            return false;
        }
        if (!confirmation.isOwnedBy(userId)) {
            return false;
        }
        return confirmation.acceptsIdempotencyKey(idempotencyKey);
    }

    /**
     * 같은 사용자의 이전 확인을 버린다.
     *
     * <p>대상이나 금액을 바꿔 다시 검토하면 앞의 확인은 <b>더 이상 사용자가 확인한 내용이
     * 아니다.</b> 남겨 두면 그 확인 ID 로 실행 요청이 들어왔을 때, 사용자가 방금 취소하거나
     * 고친 내용이 그대로 나간다. 한 사용자에게 살아 있는 확인은 언제나 하나다.
     */
    private void evictPrevious(final Long userId) {
        confirmations.entrySet().removeIf(entry -> entry.getValue().isOwnedBy(userId));
    }

    private void evictExpired(final LocalDateTime now) {
        confirmations.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
