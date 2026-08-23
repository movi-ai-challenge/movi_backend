package com.movi_backend.domain.guardian.infrastructure;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.GuardianNotificationTransactionService;
import com.movi_backend.domain.guardian.application.model.QueuedGuardianNotification;
import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardianRiskAlertDeliveryServiceTest {

    @Mock private GuardianNotificationTransactionService transactionService;
    @Mock private SmsNotificationSender smsNotificationSender;

    @InjectMocks
    private GuardianRiskAlertDeliveryService deliveryService;

    @Test
    @DisplayName("QUEUED 커밋 후 SMS가 성공하면 별도 트랜잭션으로 SENT를 기록한다")
    void QUEUED_커밋_후_SMS가_성공하면_SENT를_기록한다() {
        final QueuedGuardianNotification queued = queuedNotification();
        given(transactionService.queue(101L, RiskLevel.MEDIUM))
                .willReturn(List.of(queued));
        given(smsNotificationSender.send(
                201L,
                "encrypted-phone",
                "RISK_TRANSFER_ALERT",
                "주의 알림"
        )).willReturn("provider-message-id");

        deliveryService.deliver(101L, RiskLevel.MEDIUM);

        then(transactionService).should().markSent(201L, "provider-message-id");
        then(transactionService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("SMS가 실패하면 별도 트랜잭션으로 FAILED를 기록한다")
    void SMS가_실패하면_FAILED를_기록한다() {
        final QueuedGuardianNotification queued = queuedNotification();
        given(transactionService.queue(101L, RiskLevel.MEDIUM))
                .willReturn(List.of(queued));
        willThrow(new IllegalStateException("provider unavailable"))
                .given(smsNotificationSender)
                .send(201L, "encrypted-phone", "RISK_TRANSFER_ALERT", "주의 알림");

        deliveryService.deliver(101L, RiskLevel.MEDIUM);

        then(transactionService).should().markFailed(201L);
    }

    @Test
    @DisplayName("SMS 성공 후 SENT 저장이 실패하면 FAILED로 덮어쓰지 않는다")
    void SMS_성공_후_SENT_저장이_실패하면_FAILED로_덮어쓰지_않는다() {
        final QueuedGuardianNotification queued = queuedNotification();
        given(transactionService.queue(101L, RiskLevel.MEDIUM))
                .willReturn(List.of(queued));
        given(smsNotificationSender.send(
                201L,
                "encrypted-phone",
                "RISK_TRANSFER_ALERT",
                "주의 알림"
        )).willReturn("provider-message-id");
        willThrow(new IllegalStateException("database unavailable"))
                .given(transactionService)
                .markSent(201L, "provider-message-id");

        deliveryService.deliver(101L, RiskLevel.MEDIUM);

        then(transactionService).should().markSent(201L, "provider-message-id");
        then(transactionService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("QUEUED 저장이 실패해도 예외를 송금 호출자에게 전파하지 않는다")
    void QUEUED_저장이_실패해도_예외를_전파하지_않는다() {
        given(transactionService.queue(101L, RiskLevel.HIGH))
                .willThrow(new IllegalStateException("database unavailable"));

        deliveryService.deliver(101L, RiskLevel.HIGH);

        then(smsNotificationSender).shouldHaveNoInteractions();
    }

    private QueuedGuardianNotification queuedNotification() {
        return new QueuedGuardianNotification(
                201L,
                "encrypted-phone",
                "RISK_TRANSFER_ALERT",
                "주의 알림"
        );
    }
}
