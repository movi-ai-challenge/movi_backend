package com.movi_backend.domain.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MockSmsProviderTest {

    private static final String PHONE = "01012345678";

    private final MockSmsProvider mockSmsProvider = new MockSmsProvider();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(MockSmsProvider.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("발송에 성공하면 Provider 메시지 ID를 돌려준다")
    void 발송에_성공하면_메시지_ID를_반환한다() {
        // when
        final SmsSendResult result = mockSmsProvider.send(SmsMessage.of(PHONE, "본문"));

        // then
        assertThat(result.successful()).isTrue();
        assertThat(result.providerMessageId()).isNotBlank();
    }

    @Test
    @DisplayName("SMS 로그에 보호자 전화번호 원문을 남기지 않는다")
    void SMS_로그에_전화번호를_남기지_않는다() {
        // when
        mockSmsProvider.send(SmsMessage.of(PHONE, "[Movi] 연결 요청 문자"));

        // then
        assertThat(appender.list)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(PHONE));
    }

    @Test
    @DisplayName("SMS 로그에 본문 전체를 남기지 않는다")
    void SMS_로그에_본문을_남기지_않는다() {
        // given
        final String body = "[Movi] 초대 링크 https://movi.example/guardian/invite?token=secret";

        // when
        mockSmsProvider.send(SmsMessage.of(PHONE, body));

        // then
        assertThat(appender.list)
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain("secret"));
    }
}
