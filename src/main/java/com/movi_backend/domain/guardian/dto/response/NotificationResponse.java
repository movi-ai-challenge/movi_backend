package com.movi_backend.domain.guardian.dto.response;

import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import java.time.LocalDateTime;

/**
 * 보호자 알림 발송 기록 한 건.
 *
 * <p>발송이 실제로 나갔는지 확인하는 것이 이 응답의 목적이다. 그래서 {@code status}와 함께
 * {@code retryCount}·{@code nextRetryAt}·{@code providerMsgId}를 같이 내린다 — 상태만 보면
 * "실패했다"까지는 알아도 재시도가 도는 중인지 이미 포기했는지를 구분할 수 없다.
 *
 * <p><b>전화번호는 마스킹해서 내린다.</b> 어느 번호로 갔는지 확인하는 데 뒷자리면 충분하고,
 * 원문은 응답에 실을 이유가 없다.
 */
public record NotificationResponse(
        Long notificationId,
        Long transferId,
        NotificationChannel channel,
        String templateCode,
        String guardianName,
        String maskedGuardianPhone,
        NotificationStatus status,
        String providerMsgId,
        LocalDateTime sentAt,
        int retryCount,
        LocalDateTime nextRetryAt
) {

    public static NotificationResponse of(
            final Notification notification,
            final String maskedGuardianPhone
    ) {
        return new NotificationResponse(
                notification.getId(),
                resolveTransferId(notification),
                notification.getChannel(),
                notification.getTemplateCode(),
                notification.getGuardianLink().getGuardianName(),
                maskedGuardianPhone,
                notification.getStatus(),
                notification.getProviderMsgId(),
                notification.getSentAt(),
                notification.getRetryCount(),
                notification.getNextRetryAt()
        );
    }

    /** 이체 없이 만들어지는 알림이 나중에 생길 수 있어 null 을 허용한다. */
    private static Long resolveTransferId(final Notification notification) {
        if (notification.getTransfer() == null) {
            return null;
        }
        return notification.getTransfer().getId();
    }
}
