package com.movi_backend.domain.voice.client.dto;

import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import java.math.BigDecimal;
import java.util.List;

public record VoiceAnalysisResponse(
        String requestId,
        Long voiceSessionId,
        String transcript,
        BigDecimal sttConfidence,
        VoiceIntent intent,
        BigDecimal intentConfidence,
        VoiceEntities entities,
        VoiceEntityConfidences entityConfidences,
        List<VoiceSlot> detectedMissingEntities,
    Integer processingMs
) {

    public VoiceAnalysisResponse {
        if (detectedMissingEntities != null) {
            detectedMissingEntities = List.copyOf(detectedMissingEntities);
        }
    }

    public static VoiceAnalysisResponse of(
            final String requestId,
            final Long voiceSessionId,
            final String transcript,
            final BigDecimal sttConfidence,
            final VoiceIntent intent,
            final BigDecimal intentConfidence,
            final VoiceEntities entities,
            final VoiceEntityConfidences entityConfidences,
            final List<VoiceSlot> detectedMissingEntities,
            final Integer processingMs
    ) {
        return new VoiceAnalysisResponse(
                requestId,
                voiceSessionId,
                transcript,
                sttConfidence,
                intent,
                intentConfidence,
                entities,
                entityConfidences,
                detectedMissingEntities,
                processingMs
        );
    }
}
