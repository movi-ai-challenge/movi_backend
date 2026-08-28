package com.movi_backend.domain.guardian.infrastructure.solapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class SolapiSmsNotificationSenderTest {

    private static final String BASE_URL = "https://api.solapi.com";
    private static final Long NOTIFICATION_ID = 42L;
    private static final String ENCRYPTED_PHONE = "encrypted-phone";
    private static final String TEMPLATE_CODE = "BLOCKED_TRANSFER_ALERT";
    private static final String MESSAGE = "[Movi] 보호 대상자의 고위험 이체 요청이 감지되었습니다.";

    private static final SolapiProperties PROPERTIES = new SolapiProperties(
            "test-api-key", "test-api-secret", "01099998888", BASE_URL,
            Duration.ofSeconds(1), Duration.ofSeconds(1)
    );

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Test
    @DisplayName("발송에 성공하면 솔라피 messageId를 반환한다")
    void 발송에_성공하면_messageId를_반환한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn("01012345678");
        final SolapiSmsNotificationSender sender = sender(builder);

        server.expect(once(), requestTo(BASE_URL + "/messages/v4/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization",
                        Matchers.startsWith("HMAC-SHA256 apiKey=test-api-key")))
                .andRespond(withSuccess("""
                        {"messageId":"solapi-msg-1","groupId":"g1","statusCode":"2000","statusMessage":"success"}
                        """, MediaType.APPLICATION_JSON));

        // when
        final String messageId =
                sender.send(NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE);

        // then
        assertThat(messageId).isEqualTo("solapi-msg-1");
        server.verify();
    }

    @Test
    @DisplayName("솔라피가 실패로 응답하면 예외를 던져 재시도 대상이 되게 한다")
    void 솔라피가_실패하면_예외를_던진다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn("01012345678");
        final SolapiSmsNotificationSender sender = sender(builder);

        server.expect(once(), requestTo(BASE_URL + "/messages/v4/send"))
                .andRespond(withServerError());

        // when
        final Throwable thrown = catchThrowable(
                () -> sender.send(NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    @DisplayName("응답에 messageId가 없으면 성공으로 처리하지 않는다")
    void 응답에_messageId가_없으면_실패로_처리한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn("01012345678");
        final SolapiSmsNotificationSender sender = sender(builder);

        server.expect(once(), requestTo(BASE_URL + "/messages/v4/send"))
                .andRespond(withSuccess("""
                        {"statusCode":"3000","statusMessage":"failed"}
                        """, MediaType.APPLICATION_JSON));

        // when
        final Throwable thrown = catchThrowable(
                () -> sender.send(NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    private SolapiSmsNotificationSender sender(final RestClient.Builder builder) {
        return new SolapiSmsNotificationSender(
                builder.build(), new SolapiSignatureGenerator(), PROPERTIES, sensitiveDataCrypto);
    }
}
