package com.movi_backend.domain.transfer.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.transfer.application.TransactionQueryService;
import com.movi_backend.domain.transfer.dto.response.TransactionDetailResponse;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionQueryService transactionQueryService;

    @Test
    @DisplayName("거래내역 조회 요청은 필터와 페이징 결과를 반환하고 계좌번호는 노출하지 않는다")
    void 거래내역_조회_요청은_필터와_페이징_결과를_반환하고_계좌번호는_노출하지_않는다()
            throws Exception {
        // given
        final Long userId = 3L;
        final Long accountId = 10L;
        final TransactionResponse item = new TransactionResponse(
                101L,
                accountId,
                TransactionType.OUT,
                50_000L,
                950_000L,
                "김영희",
                "송금",
                LocalDateTime.of(2026, 8, 24, 10, 30),
                "용돈",
                TransactionSource.INTERNAL
        );
        given(transactionQueryService.findAll(
                userId,
                accountId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 24),
                TransactionType.OUT,
                0,
                20
        )).willReturn(PageResponse.of(List.of(item), 0, 20, 1));
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        final MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionQueryService))
                .setCustomArgumentResolvers(resolver)
                .build();

        // when & then
        mockMvc.perform(get("/api/transactions")
                        .header("X-Dev-User-Id", userId)
                        .queryParam("accountId", accountId.toString())
                        .queryParam("startDate", "2026-08-01")
                        .queryParam("endDate", "2026-08-24")
                        .queryParam("type", "OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("거래가 1건 있어요."))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(101L))
                .andExpect(jsonPath("$.data.content[0].accountId").value(accountId))
                .andExpect(jsonPath("$.data.content[0].type").value("OUT"))
                .andExpect(jsonPath("$.data.content[0].amount").value(50_000L))
                .andExpect(jsonPath("$.data.content[0].counterpartyName").value("김영희"))
                .andExpect(jsonPath("$.data.content[0].counterpartyAccount").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("거래가 없으면 오류가 아니라 내역이 없다는 음성 안내를 반환한다")
    void 거래가_없으면_오류가_아니라_내역이_없다는_음성_안내를_반환한다() throws Exception {
        // given
        final Long userId = 3L;
        given(transactionQueryService.findAll(userId, null, null, null, null, 0, 20))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));
        final MockMvc mockMvc = createMockMvc();

        // when & then
        mockMvc.perform(get("/api/transactions").header("X-Dev-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("그 기간에는 거래 내역이 없어요."))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("거래 상세 조회는 한국어 음성 안내를 반환하고 계좌번호는 노출하지 않는다")
    void 거래_상세_조회는_한국어_음성_안내를_반환하고_계좌번호는_노출하지_않는다() throws Exception {
        // given
        final Long userId = 3L;
        final TransactionDetailResponse detail = new TransactionDetailResponse(
                101L,
                10L,
                TransactionType.OUT,
                50_000L,
                950_000L,
                "김영희",
                "송금",
                LocalDateTime.of(2026, 8, 24, 10, 30),
                "용돈",
                TransactionSource.INTERNAL
        );
        given(transactionQueryService.findOne(userId, 101L)).willReturn(detail);
        final MockMvc mockMvc = createMockMvc();

        // when & then
        mockMvc.perform(get("/api/transactions/{transactionId}", 101L)
                        .header("X-Dev-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage")
                        .value("8월 24일 김영희 님에게 5만원 보냈어요."
                                + " 거래 뒤 잔액은 95만원이에요. 메모는 용돈이에요."))
                .andExpect(jsonPath("$.data.transactionId").value(101L))
                .andExpect(jsonPath("$.data.balanceAfter").value(950_000L))
                .andExpect(jsonPath("$.data.counterpartyName").value("김영희"))
                .andExpect(jsonPath("$.data.counterpartyAccount").doesNotExist());
    }

    private MockMvc createMockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionQueryService))
                .setCustomArgumentResolvers(
                        new CurrentUserArgumentResolver(new AuthProperties(true, 1L))
                )
                .build();
    }
}
