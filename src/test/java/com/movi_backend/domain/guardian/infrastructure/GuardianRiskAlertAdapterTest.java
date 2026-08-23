package com.movi_backend.domain.guardian.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.transfer.entity.Transfer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardianRiskAlertAdapterTest {

    private static final Long USER_ID = 3L;
    private static final String ENCRYPTED_PHONE = "encrypted-guardian-phone";

    @Mock private GuardianLinkRepository guardianLinkRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SmsNotificationSender smsNotificationSender;
    @Mock private Transfer transfer;
    @Mock private FdsAssessment assessment;
    @Mock private GuardianLink guardianLink;
    @Mock private User protectee;
    @Mock private User guardian;

    @InjectMocks
    private GuardianRiskAlertAdapter adapter;

    @Test
    @DisplayName("중위험 이체는 활성 보호자에게 주의 알림을 보내고 발송 완료로 기록한다")
    void 중위험_이체는_활성_보호자에게_주의_알림을_보낸다() {
        givenActiveGuardian(RiskLevel.MEDIUM);
        given(smsNotificationSender.send(
                ENCRYPTED_PHONE,
                GuardianRiskAlertAdapter.RISK_TRANSFER_ALERT,
                "주의가 필요한 50,000원 이체가 완료되었습니다. 앱에서 확인해 주세요."
        )).willReturn("provider-message-id");

        adapter.send(transfer, assessment);

        final Notification notification = capturedNotification();
        assertThat(notification.getTemplateCode())
                .isEqualTo(GuardianRiskAlertAdapter.RISK_TRANSFER_ALERT);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getProviderMsgId()).isEqualTo("provider-message-id");
        assertThat(notification.getTargetPhone()).isEqualTo(ENCRYPTED_PHONE);
        assertThat(notification.getPayload())
                .isEqualTo("{\"transferId\":101,\"amount\":50000,\"riskLevel\":\"MEDIUM\"}")
                .doesNotContain(ENCRYPTED_PHONE);
    }

    @Test
    @DisplayName("고위험 차단 이체는 보호자에게 차단 알림을 보낸다")
    void 고위험_차단_이체는_보호자에게_차단_알림을_보낸다() {
        givenActiveGuardian(RiskLevel.HIGH);
        given(smsNotificationSender.send(
                ENCRYPTED_PHONE,
                GuardianRiskAlertAdapter.BLOCKED_TRANSFER_ALERT,
                "고위험으로 판단된 50,000원 이체를 차단했습니다. 앱에서 확인해 주세요."
        )).willReturn("provider-message-id");

        adapter.send(transfer, assessment);

        assertThat(capturedNotification().getTemplateCode())
                .isEqualTo(GuardianRiskAlertAdapter.BLOCKED_TRANSFER_ALERT);
    }

    @Test
    @DisplayName("SMS 발송이 실패하면 알림을 실패 상태로 기록한다")
    void SMS_발송이_실패하면_알림을_실패_상태로_기록한다() {
        givenActiveGuardian(RiskLevel.MEDIUM);
        willThrow(new IllegalStateException("provider unavailable"))
                .given(smsNotificationSender)
                .send(
                        ENCRYPTED_PHONE,
                        GuardianRiskAlertAdapter.RISK_TRANSFER_ALERT,
                        "주의가 필요한 50,000원 이체가 완료되었습니다. 앱에서 확인해 주세요."
                );

        adapter.send(transfer, assessment);

        final Notification notification = capturedNotification();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getProviderMsgId()).isNull();
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    @DisplayName("활성 보호자가 없으면 알림을 저장하거나 발송하지 않는다")
    void 활성_보호자가_없으면_알림을_저장하거나_발송하지_않는다() {
        given(assessment.getRiskLevel()).willReturn(RiskLevel.MEDIUM);
        given(transfer.getUser()).willReturn(protectee);
        given(protectee.getId()).willReturn(USER_ID);
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                USER_ID,
                GuardianLinkStatus.ACTIVE
        )).willReturn(List.of());

        adapter.send(transfer, assessment);

        then(notificationRepository).shouldHaveNoInteractions();
        then(smsNotificationSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("저위험 이체는 보호자 알림 대상이 아니다")
    void 저위험_이체는_보호자_알림_대상이_아니다() {
        given(assessment.getRiskLevel()).willReturn(RiskLevel.LOW);

        adapter.send(transfer, assessment);

        then(guardianLinkRepository).shouldHaveNoInteractions();
        then(notificationRepository).shouldHaveNoInteractions();
        then(smsNotificationSender).shouldHaveNoInteractions();
    }

    private void givenActiveGuardian(final RiskLevel riskLevel) {
        given(assessment.getRiskLevel()).willReturn(riskLevel);
        given(transfer.getUser()).willReturn(protectee);
        given(protectee.getId()).willReturn(USER_ID);
        given(transfer.getId()).willReturn(101L);
        given(transfer.getAmount()).willReturn(50_000L);
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                USER_ID,
                GuardianLinkStatus.ACTIVE
        )).willReturn(List.of(guardianLink));
        given(guardianLink.getGuardianUser()).willReturn(guardian);
        given(guardianLink.getGuardianPhone()).willReturn(ENCRYPTED_PHONE);
    }

    private Notification capturedNotification() {
        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
