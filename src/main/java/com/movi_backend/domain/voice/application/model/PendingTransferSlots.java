package com.movi_backend.domain.voice.application.model;

/**
 * 확인·재질문 사이에 들고 있는 이체 슬롯.
 *
 * <p>{@code accountNumber}·{@code bankCode}는 계좌번호를 말해 준 경우다. 이름 없이 계좌로만
 * 보낼 수 있으므로, 재질문이 오가는 동안 이 값도 함께 지켜야 한다 — 금액만 다시 물었는데
 * 계좌번호를 잃으면 처음부터 다시 말해야 한다.
 */
public record PendingTransferSlots(
        Long amount,
        String recipientNickname,
        String accountNumber,
        String bankCode,
        String sourceAccountAlias,
        Long recipientId,
        Long fromAccountId,
        String confirmationId
) {

    public static PendingTransferSlots clarifying(
            final Long amount,
            final String recipientNickname,
            final String sourceAccountAlias
    ) {
        return clarifying(amount, recipientNickname, null, null, sourceAccountAlias);
    }

    public static PendingTransferSlots clarifying(
            final Long amount,
            final String recipientNickname,
            final String accountNumber,
            final String bankCode,
            final String sourceAccountAlias
    ) {
        return new PendingTransferSlots(
                amount,
                recipientNickname,
                accountNumber,
                bankCode,
                sourceAccountAlias,
                null,
                null,
                null
        );
    }

    public static PendingTransferSlots awaitingConfirmation(
            final long amount,
            final String recipientNickname,
            final String sourceAccountAlias,
            final Long recipientId,
            final Long fromAccountId,
            final String confirmationId
    ) {
        return new PendingTransferSlots(
                amount,
                recipientNickname,
                null,
                null,
                sourceAccountAlias,
                recipientId,
                fromAccountId,
                confirmationId
        );
    }
}
