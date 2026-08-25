package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;

/**
 * 이체 생성 입력.
 *
 * <p>컨트롤러 DTO를 서비스까지 그대로 끌고 가지 않기 위해 한 번 걸러 낸다. 여기 도달한 값은
 * 이미 형식 검증을 통과한 것이고, 남은 것은 도메인 규칙 검증이다.
 */
public record TransferCreateCommand(
        String idempotencyKey,
        Long fromAccountId,
        Long recipientId,
        String toBankCode,
        String toAccountNum,
        String toHolderName,
        Long amount
) {

    public static TransferCreateCommand from(final TransferExecuteRequest request) {
        return new TransferCreateCommand(
                request.idempotencyKey(),
                request.fromAccountId(),
                request.recipientId(),
                request.toBankCode(),
                request.toAccountNum(),
                request.toHolderName(),
                request.amount()
        );
    }

    public boolean hasDirectAccount() {
        return isPresent(toBankCode) && isPresent(toAccountNum) && isPresent(toHolderName);
    }

    private boolean isPresent(final String value) {
        return value != null && !value.isBlank();
    }
}
