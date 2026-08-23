package com.movi_backend.domain.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.model.QueuedGuardianNotification;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GuardianNotificationTransactionServiceTest {

    @Mock private TransferRepository transferRepository;
    @Mock private GuardianLinkRepository guardianLinkRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private Transfer transfer;
    @Mock private User protectee;
    @Mock private User guardian;
    @Mock private GuardianLink guardianLink;

    @InjectMocks
    private GuardianNotificationTransactionService service;

    @Test
    @DisplayName("활성 보호자 알림을 QUEUED 상태로 저장하고 민감정보 없는 전달 정보를 반환한다")
    void 활성_보호자_알림을_QUEUED로_저장한다() {
        givenQueueFixture();
        given(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .willAnswer(invocation -> {
                    final Notification notification = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notification, "id", 201L);
                    return notification;
                });

        final List<QueuedGuardianNotification> result = service.queue(101L, RiskLevel.MEDIUM);

        assertThat(result).singleElement().satisfies(queued -> {
            assertThat(queued.notificationId()).isEqualTo(201L);
            assertThat(queued.encryptedTargetPhone()).isEqualTo("encrypted-phone");
            assertThat(queued.templateCode()).isEqualTo("RISK_TRANSFER_ALERT");
        });
        final Notification notification = capturedNotification();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(notification.getPayload())
                .isEqualTo("{\"transferId\":101,\"amount\":50000,\"riskLevel\":\"MEDIUM\"}")
                .doesNotContain("encrypted-phone");
    }

    @Test
    @DisplayName("활성 보호자가 없으면 QUEUED 알림을 만들지 않는다")
    void 활성_보호자가_없으면_QUEUED_알림을_만들지_않는다() {
        given(transferRepository.findById(101L)).willReturn(Optional.of(transfer));
        given(transfer.getUser()).willReturn(protectee);
        given(protectee.getId()).willReturn(3L);
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                3L,
                GuardianLinkStatus.ACTIVE
        )).willReturn(List.of());

        assertThat(service.queue(101L, RiskLevel.HIGH)).isEmpty();
        then(notificationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("발송 결과는 저장된 알림의 최종 상태를 변경한다")
    void 발송_결과는_알림의_최종_상태를_변경한다() {
        final Notification sent = notification();
        final Notification failed = notification();
        given(notificationRepository.findById(201L)).willReturn(Optional.of(sent));
        given(notificationRepository.findById(202L)).willReturn(Optional.of(failed));

        service.markSent(201L, "provider-id");
        service.markFailed(202L);

        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getProviderMsgId()).isEqualTo("provider-id");
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    private void givenQueueFixture() {
        given(transferRepository.findById(101L)).willReturn(Optional.of(transfer));
        given(transfer.getUser()).willReturn(protectee);
        given(protectee.getId()).willReturn(3L);
        given(transfer.getId()).willReturn(101L);
        given(transfer.getAmount()).willReturn(50_000L);
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                3L,
                GuardianLinkStatus.ACTIVE
        )).willReturn(List.of(guardianLink));
        given(guardianLink.getGuardianUser()).willReturn(guardian);
        given(guardianLink.getGuardianPhone()).willReturn("encrypted-phone");
    }

    private Notification capturedNotification() {
        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private Notification notification() {
        return Notification.builder()
                .user(guardian)
                .guardianLink(guardianLink)
                .transfer(transfer)
                .channel(com.movi_backend.domain.guardian.type.NotificationChannel.SMS)
                .templateCode("RISK_TRANSFER_ALERT")
                .targetPhone("encrypted-phone")
                .payload("{}")
                .build();
    }
}
