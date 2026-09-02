package com.movi_backend.domain.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.guardian.dto.response.NotificationResponse;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    private static final Long USER_ID = 7L;
    private static final String ENCRYPTED_PHONE = "encrypted-guardian-phone";

    @Mock private NotificationRepository notificationRepository;
    @Mock private SensitiveDataCrypto sensitiveDataCrypto;
    @Mock private Notification notification;
    @Mock private GuardianLink guardianLink;
    @Mock private Transfer transfer;

    @InjectMocks private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("발송 상태와 재시도 정보를 함께 내려 - 실패가 재시도 중인지 포기인지 구분할 수 있다")
    void 발송_상태와_재시도_정보를_함께_내린다() {
        // given
        final LocalDateTime nextRetryAt = LocalDateTime.of(2026, 9, 2, 14, 0);
        givenNotification(NotificationStatus.FAILED, null, null, 2, nextRetryAt);
        givenPage(List.of(notification), 1);
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn("01099047809");

        // when
        final PageResponse<NotificationResponse> result =
                notificationQueryService.findMine(USER_ID, 0, 20);

        // then
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(item.retryCount()).isEqualTo(2);
            assertThat(item.nextRetryAt()).isEqualTo(nextRetryAt);
            assertThat(item.transferId()).isEqualTo(101L);
            assertThat(item.channel()).isEqualTo(NotificationChannel.SMS);
        });
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("보호자 전화번호는 마스킹해서 내린다")
    void 보호자_전화번호는_마스킹해서_내린다() {
        // given
        givenNotification(NotificationStatus.SENT, "solapi-msg-1",
                LocalDateTime.of(2026, 9, 2, 13, 53), 0, null);
        givenPage(List.of(notification), 1);
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn("01099047809");

        // when
        final PageResponse<NotificationResponse> result =
                notificationQueryService.findMine(USER_ID, 0, 20);

        // then
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.maskedGuardianPhone()).isEqualTo("010****7809");
            assertThat(item.maskedGuardianPhone()).doesNotContain("9904");
            assertThat(item.providerMsgId()).isEqualTo("solapi-msg-1");
        });
    }

    @Test
    @DisplayName("전화번호 복호화가 실패해도 나머지 발송 정보는 그대로 보여 준다")
    void 복호화가_실패해도_발송_정보는_보여_준다() {
        // given
        givenNotification(NotificationStatus.SENT, "solapi-msg-2",
                LocalDateTime.of(2026, 9, 2, 13, 53), 0, null);
        givenPage(List.of(notification), 1);
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE))
                .willThrow(new IllegalStateException("복호화 실패"));

        // when
        final PageResponse<NotificationResponse> result =
                notificationQueryService.findMine(USER_ID, 0, 20);

        // then - 번호 한 건 때문에 상태 확인 자체를 막지 않는다.
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.maskedGuardianPhone()).isNull();
            assertThat(item.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(item.providerMsgId()).isEqualTo("solapi-msg-2");
        });
    }

    @Test
    @DisplayName("알림이 없으면 빈 목록을 준다")
    void 알림이_없으면_빈_목록을_준다() {
        // given
        givenPage(List.of(), 0);

        // when
        final PageResponse<NotificationResponse> result =
                notificationQueryService.findMine(USER_ID, 0, 20);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("페이지 크기가 100을 넘으면 조회하지 않는다")
    void 페이지_크기가_상한을_넘으면_조회하지_않는다() {
        // when & then
        assertThatThrownBy(() -> notificationQueryService.findMine(USER_ID, 0, 101))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(notificationRepository).should(never()).findMine(any(), any());
    }

    @Test
    @DisplayName("최신순으로 조회한다")
    void 최신순으로_조회한다() {
        // given
        givenPage(List.of(), 0);

        // when
        notificationQueryService.findMine(USER_ID, 0, 20);

        // then
        final Pageable expected = PageRequest.of(
                0, 20, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "id"));
        then(notificationRepository).should().findMine(eq(USER_ID), eq(expected));
    }

    private void givenNotification(
            final NotificationStatus status,
            final String providerMsgId,
            final LocalDateTime sentAt,
            final int retryCount,
            final LocalDateTime nextRetryAt
    ) {
        given(notification.getId()).willReturn(11L);
        given(notification.getTransfer()).willReturn(transfer);
        given(transfer.getId()).willReturn(101L);
        given(notification.getChannel()).willReturn(NotificationChannel.SMS);
        given(notification.getTemplateCode()).willReturn("RISKY_TRANSFER_ALERT");
        given(notification.getGuardianLink()).willReturn(guardianLink);
        given(guardianLink.getGuardianName()).willReturn("김보호");
        given(guardianLink.getGuardianPhone()).willReturn(ENCRYPTED_PHONE);
        given(notification.getStatus()).willReturn(status);
        given(notification.getProviderMsgId()).willReturn(providerMsgId);
        given(notification.getSentAt()).willReturn(sentAt);
        given(notification.getRetryCount()).willReturn(retryCount);
        given(notification.getNextRetryAt()).willReturn(nextRetryAt);
    }

    private void givenPage(final List<Notification> notifications, final long total) {
        final Page<Notification> page =
                new PageImpl<>(notifications, PageRequest.of(0, 20), total);
        given(notificationRepository.findMine(eq(USER_ID), any())).willReturn(page);
    }
}
