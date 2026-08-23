package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.model.QueuedGuardianNotification;
import com.movi_backend.domain.guardian.config.NotificationRetryProperties;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuardianNotificationTransactionService {

    public static final String RISK_TRANSFER_ALERT = "RISK_TRANSFER_ALERT";
    public static final String BLOCKED_TRANSFER_ALERT = "BLOCKED_TRANSFER_ALERT";

    private final TransferRepository transferRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationRetryProperties retryProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QueuedGuardianNotification> queue(
            final Long transferId,
            final RiskLevel riskLevel
    ) {
        final Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        final List<GuardianLink> activeLinks = guardianLinkRepository
                .findAllByProtecteeUserIdAndStatus(
                        transfer.getUser().getId(),
                        GuardianLinkStatus.ACTIVE
                );
        final String templateCode = resolveTemplateCode(riskLevel);
        final String message = createMessage(transfer, riskLevel);
        final List<QueuedGuardianNotification> queued = new ArrayList<>();
        for (final GuardianLink link : activeLinks) {
            // guardianPhone과 targetPhone에는 기존 저장 암호문만 사용한다.
            final Notification notification = Notification.builder()
                    .user(link.getGuardianUser())
                    .guardianLink(link)
                    .transfer(transfer)
                    .channel(NotificationChannel.SMS)
                    .templateCode(templateCode)
                    .targetPhone(link.getGuardianPhone())
                    .payload(createPayload(transfer, riskLevel))
                    .build();
            notificationRepository.save(notification);
            queued.add(new QueuedGuardianNotification(
                    notification.getId(),
                    link.getGuardianPhone(),
                    templateCode,
                    message
            ));
        }
        return List.copyOf(queued);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(final Long notificationId, final String providerMessageId) {
        final Notification notification = findNotification(notificationId);
        notification.markSent(providerMessageId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long notificationId) {
        findNotification(notificationId).recordFailure(
                LocalDateTime.now(),
                retryProperties.maxAttempts(),
                retryProperties.delay()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QueuedGuardianNotification> findDueRetries(final LocalDateTime now) {
        return notificationRepository.findDueRetries(
                        NotificationStatus.QUEUED,
                        now,
                        PageRequest.of(0, retryProperties.batchSize())
                ).stream()
                .map(notification -> new QueuedGuardianNotification(
                        notification.getId(),
                        notification.getTargetPhone(),
                        notification.getTemplateCode(),
                        createRetryMessage(notification)
                ))
                .toList();
    }

    private Notification findNotification(final Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private String resolveTemplateCode(final RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.HIGH) {
            return BLOCKED_TRANSFER_ALERT;
        }
        return RISK_TRANSFER_ALERT;
    }

    private String createPayload(final Transfer transfer, final RiskLevel riskLevel) {
        return "{\"transferId\":%d,\"amount\":%d,\"riskLevel\":\"%s\"}"
                .formatted(transfer.getId(), transfer.getAmount(), riskLevel);
    }

    private String createMessage(final Transfer transfer, final RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.HIGH) {
            return "고위험으로 판단된 %,d원 이체를 차단했습니다. 앱에서 확인해 주세요."
                    .formatted(transfer.getAmount());
        }
        return "주의가 필요한 %,d원 이체가 완료되었습니다. 앱에서 확인해 주세요."
                .formatted(transfer.getAmount());
    }

    private String createRetryMessage(final Notification notification) {
        final RiskLevel riskLevel = BLOCKED_TRANSFER_ALERT.equals(notification.getTemplateCode())
                ? RiskLevel.HIGH
                : RiskLevel.MEDIUM;
        return createMessage(notification.getTransfer(), riskLevel);
    }
}
