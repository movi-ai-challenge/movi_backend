package com.movi_backend.domain.guardian.infrastructure;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardianNotificationRetrySchedulerTest {

    @Mock
    private GuardianRiskAlertDeliveryService deliveryService;

    @InjectMocks
    private GuardianNotificationRetryScheduler scheduler;

    @Test
    @DisplayName("스케줄 실행 시 만기 알림 재발송을 위임한다")
    void 만기_알림_재발송을_위임한다() {
        scheduler.retryDueNotifications();

        then(deliveryService).should().retryDue();
    }
}
