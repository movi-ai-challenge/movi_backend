package com.movi_backend.domain.transfer.application.model;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.voice.entity.VoiceCommand;
import java.math.BigDecimal;

public record ConfirmedTransferCommand(
        User user,
        Account fromAccount,
        TransferRecipient recipient,
        VoiceCommand voiceCommand,
        Device device,
        long amount,
        String idempotencyKey,
        BigDecimal sttConfidence
) {

    public static ConfirmedTransferCommand of(
            final User user,
            final Account fromAccount,
            final TransferRecipient recipient,
            final VoiceCommand voiceCommand,
            final Device device,
            final long amount,
            final String idempotencyKey,
            final BigDecimal sttConfidence
    ) {
        return new ConfirmedTransferCommand(
                user,
                fromAccount,
                recipient,
                voiceCommand,
                device,
                amount,
                idempotencyKey,
                sttConfidence
        );
    }
}
