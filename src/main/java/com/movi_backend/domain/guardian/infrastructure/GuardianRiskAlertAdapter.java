package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuardianRiskAlertAdapter implements TransferRiskAlertPort {

    static final String RISK_TRANSFER_ALERT = "RISK_TRANSFER_ALERT";
    static final String BLOCKED_TRANSFER_ALERT = "BLOCKED_TRANSFER_ALERT";

    private final GuardianLinkRepository guardianLinkRepository;
    private final NotificationRepository notificationRepository;
    private final SmsNotificationSender smsNotificationSender;

    @Override
    @Transactional
    public void send(final Transfer transfer, final FdsAssessment assessment) {
        if (assessment.getRiskLevel() != RiskLevel.MEDIUM
                && assessment.getRiskLevel() != RiskLevel.HIGH) {
            return;
        }
        final List<GuardianLink> activeLinks = guardianLinkRepository
                .findAllByProtecteeUserIdAndStatus(
                        transfer.getUser().getId(),
                        GuardianLinkStatus.ACTIVE
                );
        final String templateCode = resolveTemplateCode(assessment.getRiskLevel());
        for (final GuardianLink link : activeLinks) {
            sendToGuardian(transfer, assessment, link, templateCode);
        }
    }

    private void sendToGuardian(
            final Transfer transfer,
            final FdsAssessment assessment,
            final GuardianLink link,
            final String templateCode
    ) {
        // guardianPhone과 targetPhone에는 기존 저장 암호문만 사용한다.
        final Notification notification = Notification.builder()
                .user(link.getGuardianUser())
                .guardianLink(link)
                .transfer(transfer)
                .channel(NotificationChannel.SMS)
                .templateCode(templateCode)
                .targetPhone(link.getGuardianPhone())
                .payload(createPayload(transfer, assessment))
                .build();
        notificationRepository.save(notification);

        try {
            final String providerMessageId = smsNotificationSender.send(
                    link.getGuardianPhone(),
                    templateCode,
                    createMessage(transfer, assessment)
            );
            notification.markSent(providerMessageId, LocalDateTime.now());
        } catch (final RuntimeException exception) {
            notification.markFailed();
            log.warn("보호자 SMS 발송 실패: transferId={}, linkId={}",
                    transfer.getId(), link.getId());
        }
    }

    private String resolveTemplateCode(final RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.HIGH) {
            return BLOCKED_TRANSFER_ALERT;
        }
        return RISK_TRANSFER_ALERT;
    }

    private String createPayload(
            final Transfer transfer,
            final FdsAssessment assessment
    ) {
        return "{\"transferId\":%d,\"amount\":%d,\"riskLevel\":\"%s\"}"
                .formatted(transfer.getId(), transfer.getAmount(), assessment.getRiskLevel());
    }

    private String createMessage(
            final Transfer transfer,
            final FdsAssessment assessment
    ) {
        if (assessment.getRiskLevel() == RiskLevel.HIGH) {
            return "고위험으로 판단된 %,d원 이체를 차단했습니다. 앱에서 확인해 주세요."
                    .formatted(transfer.getAmount());
        }
        return "주의가 필요한 %,d원 이체가 완료되었습니다. 앱에서 확인해 주세요."
                .formatted(transfer.getAmount());
    }
}
