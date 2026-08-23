package com.movi_backend.domain.voice.application.model;

public record PendingTransferSlots(
        Long amount,
        String recipientNickname,
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
        return new PendingTransferSlots(
                amount,
                recipientNickname,
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
                sourceAccountAlias,
                recipientId,
                fromAccountId,
                confirmationId
        );
    }
}
