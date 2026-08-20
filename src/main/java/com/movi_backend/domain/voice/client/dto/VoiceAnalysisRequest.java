package com.movi_backend.domain.voice.client.dto;

import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.Resource;

public record VoiceAnalysisRequest(
        Resource audio,
        String requestId,
        Long voiceSessionId,
        VoiceIntent expectedIntent,
    List<VoiceSlot> expectedSlots
) {

    public VoiceAnalysisRequest {
        Objects.requireNonNull(audio, "audio는 필수입니다.");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId는 필수입니다.");
        }
        Objects.requireNonNull(voiceSessionId, "voiceSessionId는 필수입니다.");
        if (expectedSlots == null) {
            expectedSlots = List.of();
        } else {
            expectedSlots = List.copyOf(expectedSlots);
        }
    }

    public static VoiceAnalysisRequest of(
            final Resource audio,
            final String requestId,
            final Long voiceSessionId,
            final VoiceIntent expectedIntent,
            final List<VoiceSlot> expectedSlots
    ) {
        return new VoiceAnalysisRequest(
                audio,
                requestId,
                voiceSessionId,
                expectedIntent,
                expectedSlots
        );
    }
}
