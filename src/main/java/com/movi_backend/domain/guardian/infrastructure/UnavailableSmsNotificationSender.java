package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 운영 SMS 제공자가 연결되기 전에는 성공으로 위장하지 않고 발송 실패로 기록한다. */
@Component
@Profile("!local & !test")
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
