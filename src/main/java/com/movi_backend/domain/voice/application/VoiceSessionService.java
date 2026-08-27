package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.domain.auth.entity.Device;
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
    private final DeviceRegistrationService deviceRegistrationService;

    /**
     * 인증된 사용자의 앱 음성 세션을 생성한다.
     *
     * <p>기기를 세션에 붙여 두는 이유는 이 세션에서 시작된 이체의 FDS 평가에 신뢰 기기
     * 여부가 들어가기 때문이다. 기기를 못 찾으면 붙이지 않고 그대로 진행한다 — 비신뢰로
     * 평가돼 위험 쪽으로 기울 뿐이고, 세션 생성을 막을 이유는 아니다.
     *
     * @param userId 세션을 소유할 사용자 ID
     * @param deviceUuid 클라이언트가 보관하는 기기 식별자. 없으면 {@code null}
     * @return 생성된 세션의 공개 정보
     */
    @Transactional
    public VoiceSessionStartResponse start(final Long userId, final String deviceUuid) {
        final User user = findUser(userId);
        final Device device = deviceRegistrationService.findOwnedDevice(userId, deviceUuid);
        final VoiceSession voiceSession = VoiceSession.builder()
                .user(user)
                .device(device)
                .channel(VoiceChannel.APP)
                .build();
        final VoiceSession savedSession = voiceSessionRepository.save(voiceSession);
        return VoiceSessionStartResponse.from(savedSession);
    }

    /** 사용자 ID에 해당하는 엔티티를 조회하고 존재하지 않으면 음성 안내 가능한 예외를 던진다. */
    private User findUser(final Long userId) {
        final User user = entityManager.find(User.class, userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
