package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceSessionExpirationService {

    private final VoiceSessionRepository voiceSessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(final Long voiceSessionId, final LocalDateTime now) {
        final VoiceSession session = voiceSessionRepository.findById(voiceSessionId)
                .orElse(null);
        if (session == null || session.isClosed()) {
            return;
        }
        session.expire(now);
    }
}
