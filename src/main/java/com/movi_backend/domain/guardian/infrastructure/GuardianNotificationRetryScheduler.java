package com.movi_backend.domain.guardian.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "movi.notification.retry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GuardianNotificationRetryScheduler {

    private final GuardianRiskAlertDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${movi.notification.retry.scan-interval:30s}")
    public void retryDueNotifications() {
        deliveryService.retryDue();
    }
}
