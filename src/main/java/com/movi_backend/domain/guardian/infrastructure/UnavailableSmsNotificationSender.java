package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영 SMS 제공자가 연결되기 전에는 성공으로 위장하지 않고 발송 실패로 기록한다.
 *
 * <p>{@code movi.sms.provider=solapi}를 설정하면 솔라피 구현이 대신 등록되므로 이 대역은 빠진다.
 * 설정이 없으면 지금까지처럼 이 구현이 쓰인다.
 */
@Component
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "movi.sms", name = "provider", havingValue = "none",
        matchIfMissing = true)
public class UnavailableSmsNotificationSender implements SmsNotificationSender {

    @Override
    public String send(
            final Long notificationId,
            final String encryptedTargetPhone,
            final String templateCode,
            final String message
    ) {
        throw new IllegalStateException("SMS 제공자가 설정되지 않았습니다.");
    }
}
