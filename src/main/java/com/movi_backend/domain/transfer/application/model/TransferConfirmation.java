package com.movi_backend.domain.transfer.application.model;

import java.time.LocalDateTime;

/**
 * 화면 검토를 마친 송금의 서버 측 스냅샷.
 *
 * <p>실행 요청은 {@code confirmationId}와 멱등성 키만 보낸다. 금액·수취인·출금 계좌는 이
 * 스냅샷에서 읽으므로, 검토 화면과 실제 이체 내용이 어긋날 수 없다. 실행 요청이 금액을
 * 다시 실어 보낸다면 검토를 통과한 값과 다른 금액이 나갈 수 있다.
 *
 * <p>{@code idempotencyKey}는 첫 실행 요청에서 채워진다. 같은 확인을 다른 키로 두 번
 * 실행하려는 시도는 중복 이체이므로 거부한다.
 */
public record TransferConfirmation(
        String confirmationId,
        Long userId,
        Long fromAccountId,
        Long recipientId,
        long amount,
        LocalDateTime expiresAt,
        String idempotencyKey
) {

    public static TransferConfirmation of(
            final String confirmationId,
            final Long userId,
            final Long fromAccountId,
            final Long recipientId,
            final long amount,
            final LocalDateTime expiresAt
    ) {
        return new TransferConfirmation(
                confirmationId,
                userId,
                fromAccountId,
                recipientId,
                amount,
                expiresAt,
                null
        );
    }

    public boolean isExpired(final LocalDateTime now) {
        return !now.isBefore(this.expiresAt);
    }

    public boolean isOwnedBy(final Long userId) {
        return this.userId.equals(userId);
    }

    /** 아직 실행되지 않았거나 같은 키로 재시도하는 요청인지 여부 */
    public boolean acceptsIdempotencyKey(final String idempotencyKey) {
        if (this.idempotencyKey == null) {
            return true;
        }
        return this.idempotencyKey.equals(idempotencyKey);
    }

    public TransferConfirmation bindIdempotencyKey(final String idempotencyKey) {
        return new TransferConfirmation(
                this.confirmationId,
                this.userId,
                this.fromAccountId,
                this.recipientId,
                this.amount,
                this.expiresAt,
                idempotencyKey
        );
    }
}
