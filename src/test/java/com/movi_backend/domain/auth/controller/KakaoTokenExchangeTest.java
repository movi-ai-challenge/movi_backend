package com.movi_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.auth.application.KakaoLoginService;
import com.movi_backend.domain.auth.application.LoginHandoffStore;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 로그인 결과를 URL 이 아니라 본문으로 넘기는 교환 흐름.
 *
 * <p>기존 쿼리 방식을 끈 상태({@code legacy-token-query=false})로 검증한다. 프런트가
 * 옮겨온 뒤의 최종 모습이 이 테스트다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.kakao.frontend-redirect-uri=https://movi-ai-challenge.netlify.app/login/callback",
        "movi.kakao.legacy-token-query=false"
})
@ActiveProfiles("test")
class KakaoTokenExchangeTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private KakaoLoginService kakaoLoginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("리다이렉트 주소에 토큰이 들어가지 않는다")
    void 리다이렉트_주소에_토큰이_없다() throws Exception {
        // given
        given(kakaoLoginService.authenticate(any(), any(), any()))
                .willReturn(new LoginHandoffStore.Handoff(1L, true));

        // when
        final String location = callback();

        // then
        final var params = UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams();
        assertThat(params.getFirst("code")).isNotBlank();
        assertThat(params.getFirst("newUser")).isEqualTo("true");
        assertThat(params).doesNotContainKeys("accessToken", "refreshToken");
        assertThat(location).doesNotContain("access-token-value", "refresh-token-value");
    }

    @Test
    @DisplayName("교환 코드를 보내면 토큰을 본문으로 받는다")
    void 교환하면_토큰을_본문으로_받는다() throws Exception {
        // given
        given(kakaoLoginService.authenticate(any(), any(), any()))
                .willReturn(new LoginHandoffStore.Handoff(1L, true));
        given(kakaoLoginService.issueTokens(any(), anyBoolean())).willReturn(new LoginResponse(
                1L, true, "access-token-value", "refresh-token-value", "Bearer", 1800L
        ));

        // when & then
        mockMvc.perform(exchange(codeFrom(callback())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.data.newUser").value(true));
    }

    @Test
    @DisplayName("같은 교환 코드를 두 번 쓰면 두 번째는 거부한다")
    void 같은_코드는_한_번만_교환된다() throws Exception {
        // given — 코드가 새어 나가도 이미 교환됐으면 쓸 수 없어야 한다.
        given(kakaoLoginService.authenticate(any(), any(), any()))
                .willReturn(new LoginHandoffStore.Handoff(1L, false));
        given(kakaoLoginService.issueTokens(any(), anyBoolean())).willReturn(new LoginResponse(
                1L, false, "access-token-value", "refresh-token-value", "Bearer", 1800L
        ));
        final String code = codeFrom(callback());

        // when & then
        mockMvc.perform(exchange(code)).andExpect(status().isOk());
        mockMvc.perform(exchange(code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_4015"));
    }

    @Test
    @DisplayName("발급하지 않은 교환 코드는 거부한다")
    void 알_수_없는_코드는_거부한다() throws Exception {
        mockMvc.perform(exchange("never-issued"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_4015"));
    }

    private String callback() throws Exception {
        final MvcResult result = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code")
                        .param("state", "state-value")
                        .cookie(new Cookie("KAKAO_OAUTH_STATE", "state-value")))
                .andExpect(status().isFound())
                .andReturn();
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private String codeFrom(final String location) {
        return UriComponentsBuilder.fromUri(URI.create(location))
                .build()
                .getQueryParams()
                .getFirst("code");
    }

    private org.springframework.test.web.servlet.RequestBuilder exchange(final String code) {
        return post("/api/v1/auth/kakao/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
    }
}
