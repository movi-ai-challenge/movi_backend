package com.movi_backend.domain.guardian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보호자 연결 설정.
 *
 * @param invitationExpireHours 초대 링크 유효 시간(시). 코드에 하드코딩하지 않는다.
 * @param inviteUrl             초대 링크 기본 주소. 뒤에 {@code ?token=...}이 붙는다.
 * @param defaultReceiveAlert   {@code permission_scope}가 비어 있는 과거 데이터의 알림 수신 기본값.
 *                              <b>기능명세가 이 기본값을 확정하지 않았으므로 설정으로 뺐다.</b>
 *                              팀 정책이 정해지면 이 값만 바꾼다.
 */
@ConfigurationProperties(prefix = "movi.guardian")
public record GuardianProperties(
        Integer invitationExpireHours,
        String inviteUrl,
        Boolean defaultReceiveAlert
) {

    private static final int DEFAULT_EXPIRE_HOURS = 24;
    private static final String DEFAULT_INVITE_URL = "http://localhost:3000/guardian/invite";

    public GuardianProperties {
        if (invitationExpireHours == null || invitationExpireHours <= 0) {
            invitationExpireHours = DEFAULT_EXPIRE_HOURS;
        }
        if (inviteUrl == null || inviteUrl.isBlank()) {
            inviteUrl = DEFAULT_INVITE_URL;
        }
        if (defaultReceiveAlert == null) {
            defaultReceiveAlert = true;
        }
    }
}
