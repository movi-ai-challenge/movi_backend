package com.movi_backend.domain.guardian.infrastructure.solapi;

import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import com.movi_backend.domain.guardian.infrastructure.solapi.dto.SolapiMessage;
import com.movi_backend.domain.guardian.infrastructure.solapi.dto.SolapiSendRequest;
import com.movi_backend.domain.guardian.infrastructure.solapi.dto.SolapiSendResponse;
import com.movi_backend.global.security.SensitiveDataCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 솔라피(Solapi) SMS 발송.
 *
 * <p>{@code movi.sms.provider=solapi}일 때 {@link com.movi_backend.domain.guardian.infrastructure
 * .UnavailableSmsNotificationSender} 대신 선택된다. local·test 프로필에서는 Mock이 쓰이므로
 * 이 구현은 운영 프로필에서만 활성화한다.
 *
 * <p><b>실패는 예외로 올린다.</b> 호출부인 {@code GuardianRiskAlertDeliveryService}가
 * {@code RuntimeException}을 잡아 알림을 FAILED로 기록하고 재시도 스케줄러에 맡기는 구조라,
 * 실패를 조용히 삼키면 재시도 자체가 일어나지 않는다.
 *
 * <p>전화번호 원문과 API Secret은 로그에 남기지 않는다.
 */
@Slf4j
@Component
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "movi.sms", name = "provider", havingValue = "solapi")
public class SolapiSmsNotificationSender implements SmsNotificationSender {

    private static final String SEND_PATH = "/messages/v4/send";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final RestClient restClient;
    private final SolapiSignatureGenerator signatureGenerator;
    private final SolapiProperties properties;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    public SolapiSmsNotificationSender(
            @Qualifier("solapiRestClient") final RestClient restClient,
            final SolapiSignatureGenerator signatureGenerator,
            final SolapiProperties properties,
            final SensitiveDataCrypto sensitiveDataCrypto
    ) {
        this.restClient = restClient;
        this.signatureGenerator = signatureGenerator;
        this.properties = properties;
        this.sensitiveDataCrypto = sensitiveDataCrypto;
    }

    @Override
    public String send(
            final Long notificationId,
            final String encryptedTargetPhone,
            final String templateCode,
            final String message
    ) {
        final String targetPhone = sensitiveDataCrypto.decrypt(encryptedTargetPhone);
        final SolapiSignature signature = signatureGenerator.generate(properties.apiSecret());
        final SolapiSendRequest request = SolapiSendRequest.of(
                SolapiMessage.of(targetPhone, properties.senderPhone(), message));

        final SolapiSendResponse response;
        try {
            response = restClient.post()
                    .uri(SEND_PATH)
                    .header(AUTHORIZATION_HEADER,
                            signature.toAuthorizationHeader(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SolapiSendResponse.class);
        } catch (final RestClientException exception) {
            log.warn("솔라피 SMS 발송 실패: notificationId={} template={} reason={}",
                    notificationId, templateCode, exception.getClass().getSimpleName());
            throw new IllegalStateException("솔라피 SMS 발송에 실패했습니다.", exception);
        }

        if (response == null || response.messageId() == null) {
            log.warn("솔라피 SMS 응답에 messageId가 없습니다: notificationId={} template={}",
                    notificationId, templateCode);
            throw new IllegalStateException("솔라피 SMS 발송 결과를 확인하지 못했습니다.");
        }
        return response.messageId();
    }
}
