package com.movi_backend.domain.transfer.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.DirectTransferService;
import com.movi_backend.domain.transfer.application.TransferQueryService;
import com.movi_backend.domain.transfer.application.TransferRecipientCommandService;
import com.movi_backend.domain.transfer.application.TransferRecipientQueryService;
import com.movi_backend.domain.transfer.dto.response.TransferResultResponse;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferQueryService transferQueryService;

    @Mock
    private TransferRecipientQueryService transferRecipientQueryService;

    @Mock
    private TransferRecipientCommandService transferRecipientCommandService;

    @Mock
    private DirectTransferService directTransferService;

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
        // when & then
        mockMvc().perform(get("/api/transfers/status")
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

    @Test
    @DisplayName("차단된 직접 입력 송금도 200으로 위험도와 함께 돌려준다")
    void 차단된_직접_입력_송금도_200으로_위험도와_함께_돌려준다() throws Exception {
        // given — 차단은 요청 오류가 아니라 사용자가 알아야 할 결과다
        final Long userId = 3L;
        final String idempotencyKey = UUID.randomUUID().toString();
        final TransferResultResponse response = new TransferResultResponse(
                101L,
                TransferStatus.BLOCKED,
                RiskLevel.HIGH,
                800_000L,
                "김영희",
                null,
                List.of()
        );
        given(directTransferService.execute(eq(userId), any())).willReturn(response);

        // when & then
        mockMvc().perform(post("/api/transfers")
                        .header("X-Dev-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationId": "%s",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(UUID.randomUUID(), idempotencyKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    @DisplayName("확인 ID 없이 송금을 실행하려 하면 요청 자체를 거절한다")
    void 확인_ID_없이_송금을_실행하려_하면_요청_자체를_거절한다() throws Exception {
        // when & then
        mockMvc().perform(post("/api/transfers")
                        .header("X-Dev-User-Id", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new TransferController(
                        transferQueryService,
                        transferRecipientQueryService,
                        transferRecipientCommandService,
                        directTransferService
                ))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(
                        new AuthProperties(true, 1L)
                ))
                .build();
    }
}
