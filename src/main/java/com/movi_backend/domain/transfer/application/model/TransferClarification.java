package com.movi_backend.domain.transfer.application.model;

import com.movi_backend.domain.transfer.type.TransferSlot;
import java.util.List;

public record TransferClarification(
        List<TransferSlot> missingSlots,
        String voiceMessage
) implements TransferValidationResult {

    public TransferClarification {
        missingSlots = List.copyOf(missingSlots);
    }

    public static TransferClarification of(
            final List<TransferSlot> missingSlots,
            final String voiceMessage
    ) {
        return new TransferClarification(missingSlots, voiceMessage);
    }
}
