package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceChannel;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceSessionService {

    private final EntityManager entityManager;
    private final VoiceSessionRepository voiceSessionRepository;

    @Transactional
    public VoiceSessionStartResponse start(final Long userId) {
        final User user = findUser(userId);
        final VoiceSession voiceSession = VoiceSession.builder()
                .user(user)
                .channel(VoiceChannel.APP)
                .build();
        final VoiceSession savedSession = voiceSessionRepository.save(voiceSession);
        return VoiceSessionStartResponse.from(savedSession);
    }

    private User findUser(final Long userId) {
        final User user = entityManager.find(User.class, userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
