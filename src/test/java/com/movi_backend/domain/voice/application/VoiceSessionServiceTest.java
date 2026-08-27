package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceSessionServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long SESSION_ID = 15L;

    @Mock
    private EntityManager entityManager;

    @Mock
    private VoiceSessionRepository voiceSessionRepository;

    @Mock
    private DeviceRegistrationService deviceRegistrationService;

    @InjectMocks
    private VoiceSessionService voiceSessionService;

    @Test
    @DisplayName("음성 세션을 시작하면 활성 상태와 만료 시각을 반환한다")
    void 음성_세션을_시작하면_활성_상태와_만료_시각을_반환한다() {
        // given
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted-phone")
                .userType(UserType.VISUALLY_IMPAIRED)
                .build();
        given(entityManager.find(User.class, USER_ID)).willReturn(user);
        given(voiceSessionRepository.save(any(VoiceSession.class))).willAnswer(invocation -> {
            final VoiceSession voiceSession = invocation.getArgument(0);
            ReflectionTestUtils.setField(voiceSession, "id", SESSION_ID);
            return voiceSession;
        });

        // when
        final VoiceSessionStartResponse response = voiceSessionService.start(USER_ID, null);

        // then
        assertThat(response.voiceSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.status()).isEqualTo(VoiceSessionStatus.ACTIVE);
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 음성 세션을 시작하면 예외가 발생한다")
    void 존재하지_않는_사용자가_음성_세션을_시작하면_예외가_발생한다() {
        // given
        given(entityManager.find(User.class, USER_ID)).willReturn(null);

        // when
        final Throwable thrown = catchThrowable(() -> voiceSessionService.start(USER_ID, null));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("기기 식별자를 보내면 세션에 신뢰 기기를 연결한다")
    void 기기_식별자를_보내면_세션에_신뢰_기기를_연결한다() {
        // given — 세션에 붙은 기기가 이 세션에서 시작된 이체의 FDS 피처가 된다
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted-phone")
                .userType(UserType.VISUALLY_IMPAIRED)
                .build();
        final Device device = Device.builder()
                .user(user)
                .deviceUuid("device-uuid-1")
                .build();
        device.trust();
        given(entityManager.find(User.class, USER_ID)).willReturn(user);
        given(deviceRegistrationService.findOwnedDevice(USER_ID, "device-uuid-1"))
                .willReturn(device);
        given(voiceSessionRepository.save(any(VoiceSession.class))).willAnswer(invocation -> {
            final VoiceSession voiceSession = invocation.getArgument(0);
            ReflectionTestUtils.setField(voiceSession, "id", SESSION_ID);
            return voiceSession;
        });

        // when
        voiceSessionService.start(USER_ID, "device-uuid-1");

        // then
        final ArgumentCaptor<VoiceSession> captor = ArgumentCaptor.forClass(VoiceSession.class);
        then(voiceSessionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getDevice()).isEqualTo(device);
    }

    @Test
    @DisplayName("등록되지 않은 기기여도 세션은 만들고 기기만 비운다")
    void 등록되지_않은_기기여도_세션은_만들고_기기만_비운다() {
        // given — 기기를 못 찾는 것은 위험 쪽으로 기울 뿐, 세션을 막을 이유가 아니다
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted-phone")
                .userType(UserType.VISUALLY_IMPAIRED)
                .build();
        given(entityManager.find(User.class, USER_ID)).willReturn(user);
        given(deviceRegistrationService.findOwnedDevice(USER_ID, "unknown-uuid"))
                .willReturn(null);
        given(voiceSessionRepository.save(any(VoiceSession.class))).willAnswer(invocation -> {
            final VoiceSession voiceSession = invocation.getArgument(0);
            ReflectionTestUtils.setField(voiceSession, "id", SESSION_ID);
            return voiceSession;
        });

        // when
        final VoiceSessionStartResponse response =
                voiceSessionService.start(USER_ID, "unknown-uuid");

        // then
        assertThat(response.voiceSessionId()).isEqualTo(SESSION_ID);
        final ArgumentCaptor<VoiceSession> captor = ArgumentCaptor.forClass(VoiceSession.class);
        then(voiceSessionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getDevice()).isNull();
    }
}
