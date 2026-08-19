package com.movi_backend.domain.account.infrastructure.openbanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오픈뱅킹 연동 설정.
 *
 * @param mode      {@code mock} 이면 Mock 어댑터, {@code real} 이면 실 API 어댑터를 쓴다.
 *                  Sandbox 승인 전에는 mock 으로 개발한다.
 * @param baseUrl   실 API 기본 URL (mode=real 일 때만 사용)
 * @param clientId  오픈뱅킹 클라이언트 ID
 */
@ConfigurationProperties(prefix = "movi.openbanking")
public record OpenBankingProperties(
        String mode,
        String baseUrl,
        String clientId
) {
    public static final String MODE_MOCK = "mock";

    public OpenBankingProperties {
        if (mode == null || mode.isBlank()) {
            mode = MODE_MOCK;
        }
    }

    public boolean isMock() {
        return MODE_MOCK.equalsIgnoreCase(this.mode);
    }
}
