package com.movi_backend.domain.notification.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import com.movi_backend.domain.notification.infrastructure.SmsProvider;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 이력 생성과 발송.
 *
 * <p>흐름은 항상 {@code QUEUED -> SENT/FAILED}다. 이력을 먼저 남기고 발송하므로, Provider가
 * 응답하지 않아 프로세스가 죽어도 "보내려 했다"는 사실은 남는다.
 *
 * <p><b>발송 실패를 예외로 올려보내지 않는다.</b> 이 서비스를 호출하는 쪽은 보호자 연결 생성이나
 * 고위험 이체 차단처럼 이미 확정돼야 하는 작업이다. 문자가 실패했다고 그 결정을 되돌릴 수 없다.
 * 실패는 반환값과 {@code notifications.status}로 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SmsProvider smsProvider;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final EntityManager entityManager;

    /**
     * 알림 이력을 만들고 즉시 발송한다.
     *
     * @return 발송 결과 상태. 예외를 던지지 않는다.
     */
    @Transactional
    public NotificationStatus send(final NotificationRequest request) {
        final Notification notification = notificationRepository.save(toEntity(request));
        return dispatch(notification, request);
    }

    /**
     * 같은 이체·같은 보호자에게 같은 종류의 알림이 이미 있으면 보내지 않는다.
     *
     * <p>{@code notifications}에는 {@code (transfer_id, link_id, template_code)} UNIQUE 제약이
     * 없어 서비스에서 멱등을 지킨다.
     */
    @Transactional
    public NotificationStatus sendOnce(final NotificationRequest request) {
        if (!isAlreadySent(request)) {
            return send(request);
        }
        log.info("중복 알림 생략 transferId={} linkId={} userId={} template={}",
                request.transferId(), request.guardianLinkId(),
                request.recipientUserId(), request.template().getCode());
        return NotificationStatus.SENT;
    }

    /**
     * 같은 알림이 이미 나갔는지 본다.
     *
     * <p>보호자 알림은 {@code (transferId, linkId, template)}, 본인 알림은
     * {@code (transferId, userId, template)}로 판별한다. 본인 알림에는 {@code linkId}가 없어
     * 한 쿼리로 묶을 수 없다.
     */
    private boolean isAlreadySent(final NotificationRequest request) {
        if (request.transferId() == null) {
            return false;
        }
        if (request.guardianLinkId() != null) {
            return notificationRepository.existsByTransferIdAndGuardianLinkIdAndTemplateCode(
                    request.transferId(),
                    request.guardianLinkId(),
                    request.template().getCode()
            );
        }
        if (request.recipientUserId() == null) {
            return false;
        }
        return notificationRepository.existsByTransferIdAndUserIdAndTemplateCode(
                request.transferId(),
                request.recipientUserId(),
                request.template().getCode()
        );
    }

    private NotificationStatus dispatch(
            final Notification notification,
            final NotificationRequest request
    ) {
        final SmsMessage message = SmsMessage.of(
                request.normalizedPhone(),
                request.template().render(request.variables())
        );
        try {
            final SmsSendResult result = smsProvider.send(message);
            if (!result.successful()) {
                return markFailed(notification, "provider-rejected");
            }
            notification.markSent(result.providerMessageId(), LocalDateTime.now());
            return NotificationStatus.SENT;
        } catch (final RuntimeException exception) {
            return markFailed(notification, exception.getClass().getSimpleName());
        }
    }

    /** 실패 사유는 유형만 남긴다. Provider 오류 원문에는 수신번호가 섞여 들어오는 경우가 있다. */
    private NotificationStatus markFailed(final Notification notification, final String reasonType) {
        notification.markFailed();
        log.warn("알림 발송 실패 notificationId={} template={} reason={}",
                notification.getId(), notification.getTemplateCode(), reasonType);
        return NotificationStatus.FAILED;
    }

    private Notification toEntity(final NotificationRequest request) {
        return Notification.builder()
                .user(referenceOrNull(User.class, request.recipientUserId()))
                .guardianLink(referenceOrNull(GuardianLink.class, request.guardianLinkId()))
                .transfer(referenceOrNull(Transfer.class, request.transferId()))
                .channel(request.template().getChannel())
                .templateCode(request.template().getCode())
                .targetPhone(sensitiveDataCrypto.encrypt(request.normalizedPhone()))
                .build();
    }

    private <T> T referenceOrNull(final Class<T> entityType, final Long id) {
        if (id == null) {
            return null;
        }
        return entityManager.getReference(entityType, id);
    }
}
