package com.movi_backend.domain.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오픈뱅킹 연동 설정.
 *
 * <p>Sandbox 이용 승인이 늦어질 수 있어 <b>Mock 어댑터를 먼저 붙인다.</b> 승인이 나오면
 * {@code movi.openbanking.mode=http}로 바꾸고 구현체만 추가한다.
 *
 * @param mode        {@code mock} 또는 {@code http}
 * @param baseUrl     오픈뱅킹 API 주소
 * @param mockBalance mock 모드에서 잔액 조회 시 반환할 금액
 */
@ConfigurationProperties(prefix = "movi.openbanking")
public record OpenBankingProperties(
        String mode,
        String baseUrl,
        Long mockBalance
) {

    public static final String MODE_MOCK = "mock";
    public static final String MODE_HTTP = "http";

    private static final long DEFAULT_MOCK_BALANCE = 1_000_000L;

    public OpenBankingProperties {
        if (mode == null || mode.isBlank()) {
            mode = MODE_MOCK;
        }
        if (mockBalance == null) {
            mockBalance = DEFAULT_MOCK_BALANCE;
        }
    }
}
