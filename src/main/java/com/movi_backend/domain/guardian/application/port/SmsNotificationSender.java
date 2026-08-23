package com.movi_backend.domain.guardian.application.port;

/**
 * SMS 제공자 연동 경계.
 *
 * <p>{@code encryptedTargetPhone}은 저장된 암호문이다. 실제 제공자 구현에서만 복호화하며,
 * 구현체는 전화번호 원문을 로그에 남기지 않는다. {@code notificationId}는 재시도 시
 * 제공자에 동일하게 전달할 멱등성 키다.
 */
public interface SmsNotificationSender {

    String send(
            Long notificationId,
            String encryptedTargetPhone,
            String templateCode,
            String message
    );
}
