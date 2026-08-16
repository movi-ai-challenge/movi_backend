package com.movi_backend.domain.voice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.voice.application.VoiceSessionService;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class VoiceSessionControllerTest {

    @Mock
    private VoiceSessionService voiceSessionService;

    @Test
    @DisplayName("음성 세션 시작을 요청하면 세션 정보와 음성 안내를 반환한다")
    void 음성_세션_시작을_요청하면_세션_정보와_음성_안내를_반환한다() throws Exception {
        // given
        final Long userId = 3L;
        final LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 16, 21, 30);
        final VoiceSessionStartResponse response = new VoiceSessionStartResponse(
                15L,
                VoiceSessionStatus.ACTIVE,
                expiresAt
        );
        given(voiceSessionService.start(userId)).willReturn(response);
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        final MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceSessionController(voiceSessionService))
                .setCustomArgumentResolvers(resolver)
                .build();

        // when
        final ResultActions result = mockMvc.perform(post("/api/voice/sessions")
                .header("X-Dev-User-Id", userId));

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("무엇을 도와드릴까요?"))
                .andExpect(jsonPath("$.data.voiceSessionId").value(15L))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-16T21:30:00"));
    }
}
