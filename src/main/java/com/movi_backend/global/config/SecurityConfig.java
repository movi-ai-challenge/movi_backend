package com.movi_backend.global.config;

import com.movi_backend.global.security.AuthProperties;
import com.movi_backend.global.security.JwtAuthenticationFilter;
import com.movi_backend.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 설정.
 *
 * <p>운영에서는 로그인·토큰 갱신·Actuator 수집 경로만 공개하고 나머지는 JWT 인증을 요구한다.
 * {@code movi.auth.dev-mode=true}인 로컬 환경에서는 기존 개발용 인증 컨텍스트를 사용할 수 있다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 보호자가 아직 가입 전일 수 있어 초대 내용 조회만 인증 없이 연다. 승인·거절은 인증이 필요하다. */
    private static final String PUBLIC_INVITATION_ENDPOINT = "/api/v1/guardian-links/invitations/*";

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/kakao/**",
            "/api/v1/auth/pin/login",
            "/api/v1/auth/token/refresh",
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final AuthProperties authProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                    auth.requestMatchers(HttpMethod.GET, PUBLIC_INVITATION_ENDPOINT).permitAll();
                    if (authProperties.devMode()) {
                        auth.anyRequest().permitAll();
                        return;
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
