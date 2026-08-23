package com.movi_backend.domain.transfer.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.TransferQueryService;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferQueryService transferQueryService;

    @Test
    @DisplayName("멱등성 키로 이체 상태를 조회하면 인증 사용자의 완료 결과를 반환한다")
    void 멱등성_키로_이체_상태를_조회하면_인증_사용자의_완료_결과를_반환한다()
            throws Exception {
        // given
        final Long userId = 3L;
        final String idempotencyKey = UUID.randomUUID().toString();
        final TransferStatusResponse response = new TransferStatusResponse(
                101L,
                TransferStatus.COMPLETED,
                RiskLevel.LOW,
                50_000L,
                "김영희",
                LocalDateTime.of(2026, 8, 16, 23, 0),
                LocalDateTime.of(2026, 8, 16, 23, 0, 2)
        );
        given(transferQueryService.findStatus(userId, idempotencyKey)).willReturn(response);
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        final MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TransferController(transferQueryService))
                .setCustomArgumentResolvers(resolver)
                .build();

        // when & then
        mockMvc.perform(get("/api/transfers/status")
                        .header("X-Dev-User-Id", userId)
                        .queryParam("idempotencyKey", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("김영희 님에게 5만원을 보냈어요."))
                .andExpect(jsonPath("$.data.transferId").value(101L))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist());
    }
}
