package com.movi_backend.domain.transfer.application.model;

import com.movi_backend.domain.transfer.entity.TransferRecipient;

public record ValidatedTransferCommand(
        long amount,
        TransferRecipient recipient,
        String sourceAccountAlias
) implements TransferValidationResult {

    public static ValidatedTransferCommand of(
            final long amount,
            final TransferRecipient recipient,
            final String sourceAccountAlias
    ) {
        return new ValidatedTransferCommand(amount, recipient, sourceAccountAlias);
    }
}
