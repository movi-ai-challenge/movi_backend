package com.movi_backend.domain.guardian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보호자 연결 설정.
 *
 * @param defaultReceiveAlert {@code permission_scope}가 비어 있는 과거 데이터의 알림 수신 기본값.
 *                            <b>기능명세가 이 기본값을 확정하지 않았으므로 설정으로 뺐다.</b>
 *                            팀 정책이 정해지면 이 값만 바꾼다.
 */
@ConfigurationProperties(prefix = "movi.guardian")
public record GuardianProperties(
        Boolean defaultReceiveAlert
) {

    public GuardianProperties {
        if (defaultReceiveAlert == null) {
            defaultReceiveAlert = true;
        }
    }
}
