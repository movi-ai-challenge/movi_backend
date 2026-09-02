package com.movi_backend.domain.guardian.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MockSmsNotificationSenderTest {

    private static final Long NOTIFICATION_ID = 42L;
    private static final String TEMPLATE_CODE = "RISKY_TRANSFER_ALERT";
    private static final String MESSAGE = "주의가 필요한 100,000원 이체가 완료되었습니다. 앱에서 확인해 주세요.";
    private static final String ENCRYPTED_PHONE = "encrypted-guardian-phone";

    private final MockSmsNotificationSender sender = new MockSmsNotificationSender();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void 로그를_수집한다() {
        logger = (Logger) LoggerFactory.getLogger(MockSmsNotificationSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void 수집을_멈춘다() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("발송 문구를 로그로 남긴다 - 보호자 폰에 뜰 문구를 실제 발송 없이 확인하기 위한 것이다")
    void 발송_문구를_로그로_남긴다() {
        // when
        sender.send(NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE);

        // then
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).contains(MESSAGE);
                    assertThat(event.getFormattedMessage()).contains(TEMPLATE_CODE);
                    assertThat(event.getFormattedMessage()).contains(String.valueOf(NOTIFICATION_ID));
                });
    }

    @Test
    @DisplayName("전화번호는 암호문이라도 로그에 남기지 않는다")
    void 전화번호는_로그에_남기지_않는다() {
        // when
        sender.send(NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE);

        // then
        assertThat(appender.list)
                .singleElement()
                .satisfies(event ->
                        assertThat(event.getFormattedMessage()).doesNotContain(ENCRYPTED_PHONE));
    }

    @Test
    @DisplayName("멱등성 키로 쓸 수 있게 notificationId 를 담은 식별자를 반환한다")
    void 식별자를_반환한다() {
        // when
        final String messageId = sender.send(
                NOTIFICATION_ID, ENCRYPTED_PHONE, TEMPLATE_CODE, MESSAGE);

        // then
        assertThat(messageId).isEqualTo("mock-42");
    }
}
