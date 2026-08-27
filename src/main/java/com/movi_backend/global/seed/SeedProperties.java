package com.movi_backend.global.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시연·E2E 시드 설정.
 *
 * <p>기본값은 꺼짐이다. 시드는 실제 사용자 계정과 계좌를 만들어 내므로, 켜는 것이 명시적인
 * 선택이어야 한다. 설정을 빠뜨렸을 때 조용히 데이터가 생기는 쪽이 기본이면 안 된다.
 */
@ConfigurationProperties(prefix = "movi.seed")
public record SeedProperties(
        boolean enabled,
        String pin,
        String deviceUuid
) {

    private static final String DEFAULT_PIN = "135790";
    private static final String DEFAULT_DEVICE_UUID = "seed-device-0000-0000-0000-000000000001";

    public SeedProperties {
        if (pin == null || pin.isBlank()) {
            pin = DEFAULT_PIN;
        }
        if (deviceUuid == null || deviceUuid.isBlank()) {
            deviceUuid = DEFAULT_DEVICE_UUID;
        }
    }
}
