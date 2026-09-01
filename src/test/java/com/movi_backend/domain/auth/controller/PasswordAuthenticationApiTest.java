package com.movi_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 일반 회원가입·로그인 API 통합 검증.
 *
 * <p><b>{@code dev-mode=false}로 켠다.</b> 테스트 프로필의 기본값은 {@code true}라
 * 모든 요청이 통과하는데, 그 상태로는 가입·로그인이 실제로 공개 경로인지 확인할 수 없다.
 * 배포 후에야 401이 나는 일을 막으려면 여기서 운영과 같은 조건을 써야 한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.auth.dev-mode=false"
})
@ActiveProfiles("test")
class PasswordAuthenticationApiTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DeviceRepository deviceRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("가입하면 인증 없이도 토큰을 받고, 같은 아이디로 곧바로 로그인된다")
    void 가입하고_곧바로_로그인한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "movitester",
                                  "password": "password1234",
                                  "name": "문하늘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        // 대문자로 보내도 같은 계정을 찾아야 한다
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "MoviTester",
                                  "password": "password1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(false))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("같은 아이디로 두 번 가입할 수 없다")
    void 아이디는_중복될_수_없다() throws Exception {
        final String body = """
                {
                  "loginId": "duplicateid",
                  "password": "password1234",
                  "name": "문하늘"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_4092"))
                .andExpect(jsonPath("$.voiceMessage").isNotEmpty());
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 불일치와 같은 응답을 준다")
    void 없는_아이디도_같은_응답을_준다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "nosuchuser",
                                  "password": "password1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_4024"));
    }

    @Test
    @DisplayName("너무 짧은 비밀번호는 가입 단계에서 거부한다")
    void 짧은_비밀번호는_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "shortpw",
                                  "password": "1234",
                                  "name": "문하늘"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기기 식별자를 함께 보내도 가입되고, 그 기기가 등록된다")
    void 기기_식별자를_보내도_가입된다() throws Exception {
        // given — 프런트는 항상 deviceUuid 를 함께 보낸다. 기기 등록을 인증 트랜잭션 안에서
        //         바로 부르면 아직 커밋되지 않은 users 행의 잠금을 기다리다 교착에 빠진다.
        //         커밋 뒤에 처리되는지 확인한다.
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "deviceuser",
                                  "password": "password1234",
                                  "name": "문하늘",
                                  "deviceUuid": "device-uuid-signup",
                                  "deviceModel": "Galaxy S24",
                                  "osVersion": "Android 14"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        assertThat(deviceRepository.existsByDeviceUuid("device-uuid-signup")).isTrue();
    }
}
