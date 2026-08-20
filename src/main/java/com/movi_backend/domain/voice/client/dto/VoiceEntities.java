package com.movi_backend.domain.voice.client.dto;

import java.time.LocalDate;

public record VoiceEntities(
        Long amount,
        String recipient,
        String sourceAccountAlias,
        String bankName,
        LocalDate startDate,
        LocalDate endDate
) {

    public static VoiceEntities transfer(
            final Long amount,
            final String recipient,
            final String sourceAccountAlias
    ) {
        return new VoiceEntities(amount, recipient, sourceAccountAlias, null, null, null);
    }
}
