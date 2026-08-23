package com.movi_backend.domain.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.guardian.application.model.QueuedGuardianNotification;
import com.movi_backend.domain.guardian.config.NotificationRetryProperties;
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
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
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
    @Mock private NotificationRetryProperties retryProperties;

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
    @DisplayName("발송 실패는 두 번 재시도 예약 후 세 번째에 최종 실패 처리한다")
    void 발송_실패는_최대_횟수까지_재시도한다() {
        final Notification sent = notification();
        final Notification failed = notification();
        given(notificationRepository.findById(201L)).willReturn(Optional.of(sent));
        given(notificationRepository.findById(202L)).willReturn(Optional.of(failed));
        given(retryProperties.maxAttempts()).willReturn(3);
        given(retryProperties.delay()).willReturn(Duration.ofMinutes(1));

        service.markSent(201L, "provider-id");
        service.markFailed(202L);

        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getProviderMsgId()).isEqualTo("provider-id");
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isNotNull();

        service.markFailed(202L);
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(failed.getRetryCount()).isEqualTo(2);

        service.markFailed(202L);
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(3);
        assertThat(failed.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("재시도 시각이 지난 알림은 기존 알림 ID와 메시지로 반환한다")
    void 만기된_알림을_재시도_대상으로_반환한다() {
        final Notification notification = notification();
        ReflectionTestUtils.setField(notification, "id", 201L);
        given(transfer.getAmount()).willReturn(50_000L);
        given(retryProperties.batchSize()).willReturn(100);
        given(notificationRepository.findDueRetries(
                eq(NotificationStatus.QUEUED),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(notification));

        final List<QueuedGuardianNotification> retries = service.findDueRetries(
                LocalDateTime.now()
        );

        assertThat(retries).singleElement().satisfies(retry -> {
            assertThat(retry.notificationId()).isEqualTo(201L);
            assertThat(retry.encryptedTargetPhone()).isEqualTo("encrypted-phone");
            assertThat(retry.message()).contains("50,000원");
        });
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
