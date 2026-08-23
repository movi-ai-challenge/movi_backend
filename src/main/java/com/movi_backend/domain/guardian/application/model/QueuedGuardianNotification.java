package com.movi_backend.domain.guardian.application.model;

public record QueuedGuardianNotification(
        Long notificationId,
        String encryptedTargetPhone,
        String templateCode,
        String message
) {
}
