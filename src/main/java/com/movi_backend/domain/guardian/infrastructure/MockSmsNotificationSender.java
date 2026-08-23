package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class MockSmsNotificationSender implements SmsNotificationSender {

    @Override
    public String send(
            final Long notificationId,
            final String encryptedTargetPhone,
            final String templateCode,
            final String message
    ) {
        return "mock-" + notificationId;
    }
}
