package com.movi_backend.domain.fds.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FDS 연동 설정.
 *
 * <p>타임아웃은 docs/ai-api-contract.md의 값을 기본으로 한다(연결 1초·응답 3초·재시도 0회).
 * <b>자동 재시도를 넣지 않는다.</b> 재시도 정책이 명세에 없는 상태에서 임의로 넣으면 같은 이체가
 * 여러 번 평가되고, 그 사이 사용자는 응답을 기다린다.
 *
 * @param mode              {@code mock}이면 내부 규칙으로 판정한다. AI 파트 API가 열리면 {@code http}
 * @param baseUrl           FDS 서비스 주소
 * @param predictPath       예측 엔드포인트 경로
 * @param connectTimeout    연결 타임아웃
 * @param readTimeout       응답 타임아웃
 * @param mockHighAmount    mock 모드에서 HIGH로 판정할 금액 하한
 * @param mockMediumAmount  mock 모드에서 MEDIUM으로 판정할 금액 하한
 */
@ConfigurationProperties(prefix = "movi.fds")
public record FdsProperties(
        String mode,
        String baseUrl,
        String predictPath,
        Duration connectTimeout,
        Duration readTimeout,
        Long mockHighAmount,
        Long mockMediumAmount
) {

    public static final String MODE_MOCK = "mock";
    public static final String MODE_HTTP = "http";

    private static final String DEFAULT_PREDICT_PATH = "/internal/v1/fraud/predict";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);
    private static final long DEFAULT_MOCK_HIGH_AMOUNT = 1_000_000L;
    private static final long DEFAULT_MOCK_MEDIUM_AMOUNT = 300_000L;

    public FdsProperties {
        if (mode == null || mode.isBlank()) {
            mode = MODE_MOCK;
        }
        if (predictPath == null || predictPath.isBlank()) {
            predictPath = DEFAULT_PREDICT_PATH;
        }
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
        if (mockHighAmount == null) {
            mockHighAmount = DEFAULT_MOCK_HIGH_AMOUNT;
        }
        if (mockMediumAmount == null) {
            mockMediumAmount = DEFAULT_MOCK_MEDIUM_AMOUNT;
        }
    }

    public String predictUri() {
        return baseUrl + predictPath;
    }
}
