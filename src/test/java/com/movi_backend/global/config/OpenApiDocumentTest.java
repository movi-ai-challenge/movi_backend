package com.movi_backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

/**
 * OpenAPI 문서가 실제로 생성되는지 확인한다.
 *
 * <p>어노테이션은 컴파일만 통과해도 조용히 누락될 수 있다. 엔드포인트가 문서에서 빠지면
 * 프론트·AI 파트가 계약을 확인할 수 없으므로 생성 결과를 고정한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
class OpenApiDocumentTest {

    private static final List<String> DOCUMENTED_PATHS = List.of(
            "/api/accounts",
            "/api/accounts/balance",
            "/api/accounts/{accountId}/primary",
            "/api/accounts/{accountId}/alias",
            "/api/openbanking/connect",
            "/api/openbanking/callback",
            "/api/v1/auth/pin/login",
            "/api/v1/auth/pin/register",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/kakao/authorize",
            "/api/v1/auth/kakao/callback",
            "/api/transactions",
            "/api/transfers/status",
            "/api/voice/sessions",
            "/api/voice/sessions/{voiceSessionId}/commands"
    );

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("공개 엔드포인트가 모두 OpenAPI 문서에 나온다")
    void 공개_엔드포인트가_모두_OpenAPI_문서에_나온다() throws Exception {
        final String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(document).contains("Movi Backend API");
        for (final String path : DOCUMENTED_PATHS) {
            assertThat(document)
                    .withFailMessage("문서에 %s 가 없습니다", path)
                    .contains("\"" + path + "\"");
        }
    }

    @Test
    @DisplayName("인증 방식과 태그 설명이 문서에 담긴다")
    void 인증_방식과_태그_설명이_문서에_담긴다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.devUser.name")
                        .value("X-Dev-User-Id"))
                .andExpect(jsonPath("$.paths['/api/voice/sessions'].post.tags[0]").value("음성 명령"))
                .andExpect(jsonPath("$.paths['/api/accounts/balance'].get.summary").value("잔액조회"));
    }
}
