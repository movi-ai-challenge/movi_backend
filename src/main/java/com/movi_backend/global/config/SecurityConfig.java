package com.movi_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정.
 *
 * <p><b>현재는 개발 편의를 위해 모든 요청을 허용한다.</b> Spring Security 의존성만 추가하고
 * 설정을 두지 않으면 전 엔드포인트가 기본 인증으로 잠겨 다른 파트의 개발이 막히기 때문이다.
 *
 * <p>인증 담당자가 JWT 필터를 구현하면서 {@code permitAll()}을 실제 인가 규칙으로 교체한다.
 * 교체 전까지 이 설정으로 배포하지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TODO(auth): JWT 인증 필터 추가 후 실제 인가 규칙으로 교체
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
