package com.movi_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.auth.application.KakaoLoginService;
import com.movi_backend.domain.auth.application.LoginHandoffStore;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
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

@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.kakao.frontend-redirect-uri=https://movi-frontend-amber.vercel.app/login/callback"
})
@ActiveProfiles("test")
class KakaoAuthControllerCallbackTest {

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
    @DisplayName("카카오 로그인에 성공하면 교환 코드만 담아 프론트엔드 콜백 주소로 리다이렉트한다")
    void 카카오_로그인에_성공하면_프론트엔드로_리다이렉트한다() throws Exception {
        // given
        final LoginResponse response = new LoginResponse(
                1L, true, "access-token-value", "refresh-token-value", "Bearer", 1800L
        );
        given(kakaoLoginService.authenticate(any(), any(), any()))
                .willReturn(new LoginHandoffStore.Handoff(1L, true));
        given(kakaoLoginService.issueTokens(any(), anyBoolean())).willReturn(response);

        // when
        final MvcResult result = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code")
                        .param("state", "state-value")
                        .cookie(new jakarta.servlet.http.Cookie("KAKAO_OAUTH_STATE", "state-value")))
                .andExpect(status().isFound())
                .andReturn();

        // then
        final String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        final URI redirectUri = URI.create(location);
        final var params = UriComponentsBuilder.fromUri(redirectUri).build().getQueryParams();

        assertThat(redirectUri.getScheme() + "://" + redirectUri.getHost() + redirectUri.getPath())
                .isEqualTo("https://movi-frontend-amber.vercel.app/login/callback");
        assertThat(params.getFirst("code")).isNotBlank();
        assertThat(params.getFirst("newUser")).isEqualTo("true");

        // 이 주소는 브라우저 기록·프런트 호스트 로그·Referer 헤더에 남는다.
        // 토큰이 여기 실리면 회수할 방법이 없다.
        assertThat(params)
                .doesNotContainKeys("accessToken", "refreshToken", "tokenType", "accessTokenExpiresIn");
    }

    @Test
    @DisplayName("로그인 완료 후에는 OAuth state 쿠키를 만료시킨다")
    void 로그인_완료_후에는_state_쿠키를_만료시킨다() throws Exception {
        // given
        final LoginResponse response = new LoginResponse(
                1L, false, "access-token-value", "refresh-token-value", "Bearer", 1800L
        );
        given(kakaoLoginService.authenticate(any(), any(), any()))
                .willReturn(new LoginHandoffStore.Handoff(1L, false));
        given(kakaoLoginService.issueTokens(any(), anyBoolean())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code")
                        .param("state", "state-value")
                        .cookie(new jakarta.servlet.http.Cookie("KAKAO_OAUTH_STATE", "state-value")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
