package com.movi_backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. {@code BaseTimeEntity}의 생성·수정 시각 자동 기록에 필요하다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
