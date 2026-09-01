package com.movi_backend.domain.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.account.application.OpenBankingConnectService;
import com.movi_backend.domain.account.dto.response.ConnectResultResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 은행에서 돌아온 브라우저를 어디로 보내는지 검증한다.
 *
 * <p>이 자리에서 JSON 을 반환하면 사용자가 그 화면을 마주하고 계좌 연결이 끊긴다.
 * <b>성공이든 실패든 프런트 화면으로 돌려보내야 한다</b> — 화면을 보지 않는 사용자에게
 * 아무 데도 아닌 곳에 남는 것은 복구할 방법이 없는 상태다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.auth.dev-mode=false",
        "movi.openbanking.frontend-redirect-uri=https://movi-frontend-amber.vercel.app/accounts/connect/callback"
})
@ActiveProfiles("test")
class OpenBankingCallbackRedirectTest {

    private static final String CALLBACK_PATH = "/api/openbanking/callback";
    private static final String FRONTEND_CALLBACK =
            "https://movi-frontend-amber.vercel.app/accounts/connect/callback";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private OpenBankingConnectService connectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("연결에 성공하면 프런트 콜백 화면으로 result=success 를 달아 보낸다")
    void 성공하면_프런트로_돌려보낸다() throws Exception {
        given(connectService.completeConnect(any(), any()))
                .willReturn(ConnectResultResponse.of(2, 2));

        final var params = redirectParamsOf(mockMvc.perform(get(CALLBACK_PATH)
                        .param("code", "auth-code")
                        .param("state", "state-value"))
                .andExpect(status().isFound())
                .andReturn());

        assertThat(params.get("base")).isEqualTo(FRONTEND_CALLBACK);
        assertThat(params.get("result")).isEqualTo("success");
        assertThat(params.get("error")).isNull();
    }

    @Test
    @DisplayName("은행이 오류를 돌려줘도 프런트 콜백 화면으로 보낸다")
    void 은행_오류도_프런트로_돌려보낸다() throws Exception {
        final var params = redirectParamsOf(mockMvc.perform(get(CALLBACK_PATH)
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andReturn());

        assertThat(params.get("base")).isEqualTo(FRONTEND_CALLBACK);
        assertThat(params.get("result")).isEqualTo("error");
        assertThat(params.get("error")).isEqualTo("access_denied");
    }

    @Test
    @DisplayName("인가 코드가 없어도 프런트 콜백 화면으로 보낸다")
    void 인가_코드가_없어도_프런트로_돌려보낸다() throws Exception {
        final var params = redirectParamsOf(mockMvc.perform(get(CALLBACK_PATH))
                .andExpect(status().isFound())
                .andReturn());

        assertThat(params.get("result")).isEqualTo("error");
        assertThat(params.get("error")).isEqualTo("missing_authorization_code");
    }

    @Test
    @DisplayName("state 가 어긋나면 에러 코드만 실어 프런트로 보낸다")
    void state_가_어긋나면_에러_코드만_싣는다() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_OPENBANKING_STATE))
                .given(connectService).completeConnect(any(), any());

        final var params = redirectParamsOf(mockMvc.perform(get(CALLBACK_PATH)
                        .param("code", "auth-code")
                        .param("state", "wrong-state"))
                .andExpect(status().isFound())
                .andReturn());

        assertThat(params.get("result")).isEqualTo("error");
        assertThat(params.get("error")).isEqualTo(ErrorCode.INVALID_OPENBANKING_STATE.getCode());
    }

    @Test
    @DisplayName("인증 없이 들어와도 401 로 막지 않는다")
    void 인증_없이_들어와도_막지_않는다() throws Exception {
        // 은행이 브라우저를 보내는 자리라 사용자의 토큰을 들고 올 수 없다.
        // 여기에 인증을 걸면 계좌 연결이 항상 끊긴다.
        mockMvc.perform(get(CALLBACK_PATH).param("error", "access_denied"))
                .andExpect(status().isFound());
    }

    private java.util.Map<String, String> redirectParamsOf(final MvcResult result) {
        final String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        final URI uri = URI.create(location);
        final var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        final java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("base", uri.getScheme() + "://" + uri.getHost() + uri.getPath());
        params.put("result", query.getFirst("result"));
        params.put("error", query.getFirst("error"));
        return params;
    }
}
