package com.movi_backend.domain.notification.infrastructure.solapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 솔라피(Solapi) SMS 발송 설정.
 *
 * @param apiKey       솔라피 API Key
 * @param apiSecret    솔라피 API Secret. HMAC-SHA256 서명에만 쓰고 로그에 남기지 않는다.
 * @param senderPhone  사전 등록한 발신번호. 솔라피는 등록하지 않은 발신번호의 발송을 거부한다.
 * @param baseUrl      솔라피 API 주소
 */
@ConfigurationProperties(prefix = "movi.sms.solapi")
public record SolapiProperties(
        String apiKey,
        String apiSecret,
        String senderPhone,
        String baseUrl,
        Duration connectTimeout,
        Duration responseTimeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.solapi.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    public SolapiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (responseTimeout == null) {
            responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
        }
    }
}
