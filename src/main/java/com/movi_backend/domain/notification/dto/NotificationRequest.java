package com.movi_backend.domain.notification.dto;

import com.movi_backend.domain.notification.type.NotificationTemplate;
import java.util.Map;

/**
 * 알림 1건 발송 요청.
 *
 * <p>엔티티가 아니라 <b>식별자만</b> 담는다. 이체 관련 알림은 이체 트랜잭션이 끝난 뒤 다른
 * 스레드에서 발송되므로, 영속성 컨텍스트에 묶인 엔티티를 그대로 넘기면 스레드 경계에서 깨진다.
 *
 * @param recipientUserId 수신자 회원 ID. 미가입 보호자면 {@code null}
 * @param guardianLinkId  관련 보호자 연결 ID. 본인에게 보내는 알림이면 {@code null}
 * @param transferId      이 알림을 유발한 이체 ID. 초대 문자처럼 이체와 무관하면 {@code null}
 * @param normalizedPhone 정규화된 수신 전화번호 평문
 * @param variables       템플릿 치환 변수
 */
public record NotificationRequest(
        Long recipientUserId,
        Long guardianLinkId,
        Long transferId,
        NotificationTemplate template,
        String normalizedPhone,
        Map<String, String> variables
) {

    /** 보호자 초대 문자 */
    public static NotificationRequest guardianInvite(
            final Long recipientUserId,
            final Long guardianLinkId,
            final String normalizedPhone,
            final Map<String, String> variables
    ) {
        return new NotificationRequest(
                recipientUserId,
                guardianLinkId,
                null,
                NotificationTemplate.GUARDIAN_INVITE,
                normalizedPhone,
                variables
        );
    }

    /** 이체 관련 보호자 알림 */
    public static NotificationRequest guardianTransferAlert(
            final Long recipientUserId,
            final Long guardianLinkId,
            final Long transferId,
            final NotificationTemplate template,
            final String normalizedPhone
    ) {
        return new NotificationRequest(
                recipientUserId,
                guardianLinkId,
                transferId,
                template,
                normalizedPhone,
                Map.of()
        );
    }

    /**
     * 거래 당사자 본인에게 보내는 알림.
     *
     * <p>보호자 연결과 무관하므로 {@code guardianLinkId}가 없다. 본인이 요청하지 않은 이체가
     * 시도됐을 때 스스로 알아챌 수 있어야 해서, 보호자와 별개로 보낸다.
     */
    public static NotificationRequest selfTransferAlert(
            final Long recipientUserId,
            final Long transferId,
            final NotificationTemplate template,
            final String normalizedPhone
    ) {
        return new NotificationRequest(
                recipientUserId,
                null,
                transferId,
                template,
                normalizedPhone,
                Map.of()
        );
    }
}
