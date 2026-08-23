package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.GuardianNotificationTransactionService;
import com.movi_backend.domain.guardian.application.model.QueuedGuardianNotification;
import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuardianRiskAlertDeliveryService {

    private final GuardianNotificationTransactionService transactionService;
    private final SmsNotificationSender smsNotificationSender;

    public void deliver(final Long transferId, final RiskLevel riskLevel) {
        final List<QueuedGuardianNotification> notifications;
        try {
            notifications = transactionService.queue(transferId, riskLevel);
        } catch (final RuntimeException exception) {
            log.warn("보호자 알림 대기열 저장 실패: transferId={}", transferId);
            return;
        }

        for (final QueuedGuardianNotification notification : notifications) {
            deliver(notification);
        }
    }

    public void retryDue() {
        final List<QueuedGuardianNotification> notifications;
        try {
            notifications = transactionService.findDueRetries(LocalDateTime.now());
        } catch (final RuntimeException exception) {
            log.warn("보호자 알림 재시도 대상 조회 실패");
            return;
        }
        for (final QueuedGuardianNotification notification : notifications) {
            deliver(notification);
        }
    }

    private void deliver(final QueuedGuardianNotification notification) {
        final String providerMessageId;
        try {
            providerMessageId = smsNotificationSender.send(
                    notification.notificationId(),
                    notification.encryptedTargetPhone(),
                    notification.templateCode(),
                    notification.message()
            );
        } catch (final RuntimeException exception) {
            markFailed(notification.notificationId());
            return;
        }

        try {
            transactionService.markSent(notification.notificationId(), providerMessageId);
        } catch (final RuntimeException exception) {
            // 발송은 성공했으므로 FAILED로 덮어쓰지 않는다. 같은 알림 ID로 상태 복구·재조회한다.
            log.warn("보호자 알림 성공 상태 저장 실패: notificationId={}",
                    notification.notificationId());
        }
    }

    private void markFailed(final Long notificationId) {
        try {
            transactionService.markFailed(notificationId);
        } catch (final RuntimeException exception) {
            log.warn("보호자 알림 실패 상태 저장 실패: notificationId={}", notificationId);
        }
    }
}
