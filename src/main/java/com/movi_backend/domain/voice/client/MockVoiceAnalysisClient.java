package com.movi_backend.domain.voice.client;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntities;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "movi.voice",
        name = "client-type",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockVoiceAnalysisClient implements VoiceAnalysisClient {

    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.95");

    @Override
    public VoiceAnalysisResponse analyze(final VoiceAnalysisRequest request) {
        if (request.expectedSlots().contains(VoiceSlot.AMOUNT)) {
            return amountFollowUp(request);
        }
        return normalTransfer(request);
    }

    private VoiceAnalysisResponse normalTransfer(final VoiceAnalysisRequest request) {
        return VoiceAnalysisResponse.of(
                request.requestId(),
                request.voiceSessionId(),
                "엄마한테 오만 원 보내줘",
                HIGH_CONFIDENCE,
                VoiceIntent.TRANSFER,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(50_000L, "엄마", null),
                VoiceEntityConfidences.transfer(HIGH_CONFIDENCE, HIGH_CONFIDENCE, null),
                List.of(),
                100
        );
    }

    private VoiceAnalysisResponse amountFollowUp(final VoiceAnalysisRequest request) {
        return VoiceAnalysisResponse.of(
                request.requestId(),
                request.voiceSessionId(),
                "오만 원",
                HIGH_CONFIDENCE,
                VoiceIntent.TRANSFER,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(50_000L, null, null),
                VoiceEntityConfidences.transfer(HIGH_CONFIDENCE, null, null),
                List.of(VoiceSlot.RECIPIENT),
                100
        );
    }
}
