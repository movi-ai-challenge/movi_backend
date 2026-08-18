package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
        final VoiceSessionStartResponse response = voiceSessionService.start(USER_ID);

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
        final Throwable thrown = catchThrowable(() -> voiceSessionService.start(USER_ID));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
