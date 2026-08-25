package com.movi_backend.domain.notification.infrastructure.solapi;

import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import com.movi_backend.domain.notification.infrastructure.SmsProvider;
import com.movi_backend.domain.notification.infrastructure.solapi.dto.SolapiMessage;
import com.movi_backend.domain.notification.infrastructure.solapi.dto.SolapiSendRequest;
import com.movi_backend.domain.notification.infrastructure.solapi.dto.SolapiSendResponse;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 솔라피(Solapi) SMS 발송.
 *
 * <p>{@code movi.sms.provider=solapi}일 때 선택된다. {@link SmsProvider} 계약대로 실패를
 * 예외로 올리지 않고 {@link SmsSendResult#failure()}로 돌려준다 — 이미 확정된 이체 차단이나
 * 보호자 등록을 문자 발송 실패 때문에 되돌리면 안 되기 때문이다.
 *
 * <p>수신번호·발신번호·API Secret은 로그에 원문으로 남기지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "movi.sms", name = "provider", havingValue = "solapi")
public class SolapiSmsProvider implements SmsProvider {

    private static final String SEND_PATH = "/messages/v4/send";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final RestClient restClient;
    private final SolapiSignatureGenerator signatureGenerator;
    private final SolapiProperties properties;

    public SolapiSmsProvider(
            @Qualifier("solapiRestClient") final RestClient restClient,
            final SolapiSignatureGenerator signatureGenerator,
            final SolapiProperties properties
    ) {
        this.restClient = restClient;
        this.signatureGenerator = signatureGenerator;
        this.properties = properties;
    }

    @Override
    public SmsSendResult send(final SmsMessage message) {
        final SolapiSignature signature = signatureGenerator.generate(properties.apiSecret());
        final SolapiSendRequest request = SolapiSendRequest.of(
                SolapiMessage.of(message.targetPhone(), properties.senderPhone(), message.text()));
        try {
            final SolapiSendResponse response = restClient.post()
                    .uri(SEND_PATH)
                    .header(AUTHORIZATION_HEADER, signature.toAuthorizationHeader(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SolapiSendResponse.class);
            return toResult(response);
        } catch (final RestClientException exception) {
            log.warn("솔라피 SMS 발송 실패 to={} reason={}",
                    PhoneNumberNormalizer.mask(message.targetPhone()),
                    exception.getClass().getSimpleName());
            return SmsSendResult.failure();
        }
    }

    private SmsSendResult toResult(final SolapiSendResponse response) {
        if (response == null || response.messageId() == null) {
            return SmsSendResult.failure();
        }
        return SmsSendResult.success(response.messageId());
    }
}
