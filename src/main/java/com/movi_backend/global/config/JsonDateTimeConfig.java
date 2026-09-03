package com.movi_backend.global.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 날짜·시각을 브라우저가 반드시 읽을 수 있는 형식으로 내보낸다.
 *
 * <p>기본 설정은 {@code LocalDateTime} 을 나노초까지 적어
 * {@code 2026-09-03T16:42:49.704198072} 같은 값을 만든다. 그런데 ECMAScript 가 정한
 * 날짜 문자열 형식은 소수점 이하 <b>세 자리</b>까지다. 그보다 길면 표준 밖이라
 * {@code Date.parse} 의 동작이 엔진마다 갈린다 — 크롬은 읽어 주지만 사파리는
 * {@code NaN} 을 돌려준다.
 *
 * <p>실제로 이 때문에 아이폰에서 음성 송금이 막혔다. 백엔드는 확인 질문을 정상으로
 * 만들어 보냈는데, 프런트가 {@code expiresAt} 을 날짜로 읽지 못해 응답 전체를 버렸다.
 * 사용자에게는 "잠시 문제가 생겼어요"만 남았다.
 *
 * <p>화면을 보지 않는 사용자에게 이런 실패는 특히 나쁘다. 무엇이 잘못됐는지 볼 수 없고,
 * 다시 말해도 같은 지점에서 또 막힌다.
 */
@Configuration
public class JsonDateTimeConfig {

    /** ECMAScript 날짜 문자열 형식. 밀리초 세 자리까지만 적는다. */
    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    @Bean
    public JsonMapperBuilderCustomizer browserSafeDateTimeCustomizer() {
        return builder -> builder.addModule(
                new SimpleModule().addSerializer(
                        LocalDateTime.class,
                        new LocalDateTimeSerializer(ISO_MILLIS)
                )
        );
    }
}
