package com.movi_backend.domain.notification.dto;

/**
 * SMS Provider에 넘기는 발송 단위.
 *
 * <p>{@code targetPhone}은 복호화된 평문이다. Provider 호출 직전에만 만들고,
 * <b>로그·예외 메시지·재시도 큐에 남기지 않는다.</b>
 */
public record SmsMessage(
        String targetPhone,
        String text
) {
    public static SmsMessage of(final String targetPhone, final String text) {
        return new SmsMessage(targetPhone, text);
    }
}
