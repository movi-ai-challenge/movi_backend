package com.movi_backend.domain.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;
import com.movi_backend.domain.notification.infrastructure.SmsProvider;
import com.movi_backend.domain.notification.type.NotificationTemplate;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long NOTIFICATION_ID = 101L;
    private static final Long LINK_ID = 15L;
    private static final Long TRANSFER_ID = 77L;
    private static final String NORMALIZED_PHONE = "01012345678";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SmsProvider smsProvider;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("발송에 성공하면 알림 상태를 SENT로 바꾼다")
    void SMS_발송에_성공하면_알림_상태를_SENT로_변경한다() {
        // given
        givenSavedNotification();
        given(smsProvider.send(any(SmsMessage.class)))
                .willReturn(SmsSendResult.success("provider-message-1"));

        // when
        final NotificationStatus status = notificationService.send(registrationNoticeRequest());

        // then
        assertThat(status).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Provider가 실패를 반환하면 알림 상태를 FAILED로 바꾼다")
    void SMS_발송에_실패하면_알림_상태를_FAILED로_변경한다() {
        // given
        givenSavedNotification();
        given(smsProvider.send(any(SmsMessage.class))).willReturn(SmsSendResult.failure());

        // when
        final NotificationStatus status = notificationService.send(registrationNoticeRequest());

        // then
        assertThat(status).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("Provider가 예외를 던져도 호출자에게 전파하지 않는다")
    void Provider_예외는_전파하지_않는다() {
        // given
        givenSavedNotification();
        willThrow(new IllegalStateException("provider down"))
                .given(smsProvider).send(any(SmsMessage.class));

        // when
        final NotificationStatus status = notificationService.send(registrationNoticeRequest());

        // then
        assertThat(status).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("발송 대상 전화번호는 암호화해서 저장한다")
    void 수신번호를_암호화해서_저장한다() {
        // given
        givenSavedNotification();
        given(smsProvider.send(any(SmsMessage.class)))
                .willReturn(SmsSendResult.success("provider-message-1"));

        // when
        notificationService.send(registrationNoticeRequest());

        // then
        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getTargetPhone()).isEqualTo("encrypted-phone");
        assertThat(captor.getValue().getTargetPhone()).isNotEqualTo(NORMALIZED_PHONE);
    }

    @Test
    @DisplayName("같은 이체·같은 보호자에게 같은 알림이 이미 나갔으면 다시 보내지 않는다")
    void 중복_알림은_보내지_않는다() {
        // given
        given(notificationRepository.existsByTransferIdAndGuardianLinkIdAndTemplateCode(
                TRANSFER_ID, LINK_ID, NotificationTemplate.BLOCKED_TRANSFER_ALERT.getCode()))
                .willReturn(true);

        // when
        final NotificationStatus status = notificationService.sendOnce(alertRequest());

        // then
        assertThat(status).isEqualTo(NotificationStatus.SENT);
        then(smsProvider).should(never()).send(any(SmsMessage.class));
        then(notificationRepository).should(never()).save(any(Notification.class));
    }

    private void givenSavedNotification() {
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn("encrypted-phone");
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> {
                    final Notification saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", NOTIFICATION_ID);
                    return saved;
                });
    }

    private NotificationRequest registrationNoticeRequest() {
        return NotificationRequest.guardianLinkRegistered(
                null,
                LINK_ID,
                NORMALIZED_PHONE,
                Map.of("protecteeName", "홍길동")
        );
    }

    private NotificationRequest alertRequest() {
        return NotificationRequest.guardianTransferAlert(
                null,
                LINK_ID,
                TRANSFER_ID,
                NotificationTemplate.BLOCKED_TRANSFER_ALERT,
                NORMALIZED_PHONE
        );
    }
}
