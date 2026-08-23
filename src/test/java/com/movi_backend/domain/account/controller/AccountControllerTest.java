package com.movi_backend.domain.account.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.account.application.AccountService;
import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.global.error.GlobalExceptionHandler;
import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.CurrentUserArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private BalanceInquiryService balanceInquiryService;

    @Mock
    private AccountService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(
                new AuthProperties(true, 1L)
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(balanceInquiryService, accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(resolver)
                .build();
    }

    @Test
    @DisplayName("계좌 별칭을 변경하면 변경된 계좌와 음성 안내를 반환한다")
    void 계좌_별칭을_변경하면_변경된_계좌와_음성_안내를_반환한다() throws Exception {
        final Long userId = 3L;
        final Long accountId = 12L;
        final AccountResponse response = new AccountResponse(
                accountId,
                "국민은행",
                "123-***-456789",
                "월급통장",
                AccountType.DEPOSIT,
                false
        );
        given(accountService.changeAlias(userId, accountId, "월급통장"))
                .willReturn(response);

        mockMvc.perform(patch("/api/accounts/{accountId}/alias", accountId)
                        .header("X-Dev-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"  월급통장  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.voiceMessage").value("계좌 별칭을 바꿨어요."))
                .andExpect(jsonPath("$.data.accountId").value(accountId))
                .andExpect(jsonPath("$.data.accountAlias").value("월급통장"));
    }

    @Test
    @DisplayName("빈 계좌 별칭을 요청하면 잘못된 요청 응답을 반환한다")
    void 빈_계좌_별칭을_요청하면_잘못된_요청_응답을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/alias", 12L)
                        .header("X-Dev-User-Id", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQ_4000"));
    }
}
