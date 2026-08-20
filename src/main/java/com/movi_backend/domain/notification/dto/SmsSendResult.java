package com.movi_backend.domain.notification.dto;

/**
 * SMS 발송 결과.
 *
 * <p>실패 사유를 Provider 원문 그대로 담지 않는다. 원문에는 수신번호가 섞여 들어오는 경우가 있다.
 */
public record SmsSendResult(
        boolean successful,
        String providerMessageId
) {
    public static SmsSendResult success(final String providerMessageId) {
        return new SmsSendResult(true, providerMessageId);
    }

    public static SmsSendResult failure() {
        return new SmsSendResult(false, null);
    }
}
