package com.movi_backend.domain.voice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.voice.application.VoiceCommandResultStore;
import com.movi_backend.domain.voice.application.VoiceCommandService;
import com.movi_backend.domain.voice.application.VoiceSessionService;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
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

    @Mock
    private VoiceCommandService voiceCommandService;

    @Mock
    private VoiceCommandResultStore voiceCommandResultStore;

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
        given(voiceSessionService.start(userId, null)).willReturn(response);
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        final MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceSessionController(
                        voiceSessionService,
                        voiceCommandService,
                        voiceCommandResultStore
                ))
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

    @Test
    @DisplayName("음성 명령을 보내면 재질문 정보와 음성 안내를 반환한다")
    void 음성_명령을_보내면_재질문_정보와_음성_안내를_반환한다() throws Exception {
        // given
        final Long userId = 3L;
        final Long sessionId = 15L;
        final LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 16, 21, 31);
        final VoiceCommandResponse response = new VoiceCommandResponse(
                sessionId,
                VoiceSessionStatus.CLARIFYING,
                VoiceIntent.TRANSFER,
                "엄마 계좌 ***3456으로 보내줘",
                List.of(TransferSlot.AMOUNT),
                null,
                null,
                null,
                null,
                expiresAt,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null
        );
        final org.springframework.mock.web.MockMultipartFile audio =
                new org.springframework.mock.web.MockMultipartFile(
                        "audio",
                        "voice.webm",
                        "audio/webm",
                        new byte[]{1, 2, 3}
                );
        given(voiceCommandService.process(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(sessionId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("confirmation-123"),
                org.mockito.ArgumentMatchers.eq("550e8400-e29b-41d4-a716-446655440000")
        )).willReturn(response);
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        final MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new VoiceSessionController(
                        voiceSessionService,
                        voiceCommandService,
                        voiceCommandResultStore
                ))
                .setCustomArgumentResolvers(resolver)
                .build();

        // when
        final ResultActions result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/voice/sessions/{voiceSessionId}/commands", sessionId)
                        .file(audio)
                        .part(new org.springframework.mock.web.MockPart(
                                "confirmationId",
                                "confirmation-123".getBytes(StandardCharsets.UTF_8)
                        ))
                        .part(new org.springframework.mock.web.MockPart(
                                "idempotencyKey",
                                "550e8400-e29b-41d4-a716-446655440000"
                                        .getBytes(StandardCharsets.UTF_8)
                        ))
                        .header("X-Dev-User-Id", userId)
        );

        // then
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("얼마를 보내시겠어요?"))
                .andExpect(jsonPath("$.data.state").value("CLARIFYING"))
                .andExpect(jsonPath("$.data.transcript")
                        .value("엄마 계좌 ***3456으로 보내줘"))
                .andExpect(jsonPath("$.data.missingSlots[0]").value("AMOUNT"));
    }
}
