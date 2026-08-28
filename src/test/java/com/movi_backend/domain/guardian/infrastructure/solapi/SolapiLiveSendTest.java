package com.movi_backend.domain.guardian.infrastructure.solapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.global.security.CryptoProperties;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 솔라피 실발송 점검.
 *
 * <p><b>실제 문자가 나가고 발송 비용이 든다.</b> 그래서 평소 테스트 스위트에서는 돌지 않는다.
 * {@code SOLAPI_LIVE_TEST=true}가 있을 때만 실행되며, 인증정보와 번호도 전부 환경변수로 받는다.
 * 코드에 키를 적어 두면 저장소에 남는다.
 *
 * <pre>
 * SOLAPI_LIVE_TEST=true \
 * SOLAPI_API_KEY=... SOLAPI_API_SECRET=... \
 * SOLAPI_SENDER_PHONE=발신번호 SOLAPI_TARGET_PHONE=수신번호 \
 * ./gradlew test --tests "*SolapiLiveSendTest*"
 * </pre>
 *
 * <p>운영 경로를 그대로 태운다 — 번호를 암호화해 넘기고 구현체가 복호화하게 한다.
 * 여기서 성공해야 보호자 경고 문자가 실제로 도착한다.
 */
@EnabledIfEnvironmentVariable(named = "SOLAPI_LIVE_TEST", matches = "true")
class SolapiLiveSendTest {

    /** 이 테스트에서만 쓰는 임시 키. 운영 키와 무관하며 문자 본문에 영향을 주지 않는다. */
    private static final String TEST_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String TEST_HASH_KEY = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    private static final Long NOTIFICATION_ID = 1L;
    private static final String TEMPLATE_CODE = "LIVE_CHECK";

    @Test
    @DisplayName("솔라피로 실제 문자를 보내고 messageId를 받는다")
    void 솔라피로_실제_문자를_보낸다() {
        // given
        final SolapiProperties properties = new SolapiProperties(
                requiredEnv("SOLAPI_API_KEY"),
                requiredEnv("SOLAPI_API_SECRET"),
                requiredEnv("SOLAPI_SENDER_PHONE"),
                null,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
        );
        final SensitiveDataCrypto crypto = new SensitiveDataCrypto(
                new CryptoProperties(TEST_ENCRYPTION_KEY, TEST_HASH_KEY));
        final SolapiSmsNotificationSender sender = new SolapiSmsNotificationSender(
                // RestClient 는 SolapiClientConfig 와 같은 방식으로 만든다.
                // 기본 팩토리(JDK HttpClient)를 쓰면 운영과 다른 스택을 검증하게 되고,
                // socksProxyHost 같은 JVM 프록시 설정도 무시돼 우회 검증이 불가능하다.
                RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(requestFactory(properties))
                        .build(),
                new SolapiSignatureGenerator(),
                properties,
                crypto
        );

        final String targetPhone = requiredEnv("SOLAPI_TARGET_PHONE");
        final String message = requiredEnv("SOLAPI_MESSAGE");

        // when
        final String messageId = sender.send(
                NOTIFICATION_ID,
                crypto.encrypt(targetPhone),
                TEMPLATE_CODE,
                message
        );

        // then
        assertThat(messageId).isNotBlank();
        System.out.println("솔라피 발송 완료 messageId=" + messageId);
    }

    private SimpleClientHttpRequestFactory requestFactory(final SolapiProperties properties) {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.responseTimeout());
        return factory;
    }

    private String requiredEnv(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
