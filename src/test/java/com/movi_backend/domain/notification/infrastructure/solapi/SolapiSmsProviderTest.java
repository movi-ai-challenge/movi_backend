package com.movi_backend.domain.notification.infrastructure.solapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SolapiSmsProviderTest {

    private static final String BASE_URL = "https://api.solapi.com";
    private static final SolapiProperties PROPERTIES = new SolapiProperties(
            "test-api-key", "test-api-secret", "01099998888", BASE_URL,
            Duration.ofSeconds(1), Duration.ofSeconds(1)
    );

    @Test
    @DisplayName("발송에 성공하면 messageId를 담아 성공 결과를 반환한다")
    void 발송에_성공하면_성공_결과를_반환한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final SolapiSmsProvider provider = new SolapiSmsProvider(
                builder.build(), new SolapiSignatureGenerator(), PROPERTIES);

        server.expect(once(), requestTo(BASE_URL + "/messages/v4/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", Matchers.startsWith("HMAC-SHA256 apiKey=test-api-key")))
                .andRespond(withSuccess("""
                        {"messageId":"solapi-msg-1","groupId":"g1","statusCode":"2000","statusMessage":"success"}
                        """, MediaType.APPLICATION_JSON));

        // when
        final SmsSendResult result = provider.send(SmsMessage.of("01012345678", "위험 거래가 감지됐어요."));

        // then
        assertThat(result.successful()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("solapi-msg-1");
        server.verify();
    }

    @Test
    @DisplayName("솔라피 응답이 실패하면 예외를 던지지 않고 실패 결과를 반환한다")
    void 솔라피_응답이_실패하면_실패_결과를_반환한다() {
        // given
        final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final SolapiSmsProvider provider = new SolapiSmsProvider(
                builder.build(), new SolapiSignatureGenerator(), PROPERTIES);

        server.expect(once(), requestTo(BASE_URL + "/messages/v4/send"))
                .andRespond(withServerError());

        // when
        final SmsSendResult result = provider.send(SmsMessage.of("01012345678", "위험 거래가 감지됐어요."));

        // then
        assertThat(result.successful()).isFalse();
        assertThat(result.providerMessageId()).isNull();
        server.verify();
    }
}
