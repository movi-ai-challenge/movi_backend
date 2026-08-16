package com.movi_backend.domain.voice.dto.response;

import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import java.time.LocalDateTime;

public record VoiceSessionStartResponse(
        Long voiceSessionId,
        VoiceSessionStatus status,
        LocalDateTime expiresAt
) {

    public static VoiceSessionStartResponse from(final VoiceSession voiceSession) {
        return new VoiceSessionStartResponse(
                voiceSession.getId(),
                voiceSession.getStatus(),
                voiceSession.getExpiresAt()
        );
    }
}
