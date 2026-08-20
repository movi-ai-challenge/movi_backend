package com.movi_backend.domain.notification.infrastructure;

import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발·테스트용 SMS Provider.
 *
 * <p>실제 발송 없이 성공으로 처리한다. {@code movi.sms.provider} 설정이 없거나 {@code mock}이면
 * 이 구현이 선택된다.
 *
 * <p><b>수신번호는 마스킹해서만 남긴다.</b> 본문에도 링크가 들어가므로 전체를 찍지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "movi.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsProvider implements SmsProvider {

    private static final String MOCK_MESSAGE_ID_PREFIX = "mock-";

    @Override
    public SmsSendResult send(final SmsMessage message) {
        log.info("[MOCK SMS] to={} length={}",
                PhoneNumberNormalizer.mask(message.targetPhone()),
                message.text().length());
        return SmsSendResult.success(MOCK_MESSAGE_ID_PREFIX + UUID.randomUUID());
    }
}
