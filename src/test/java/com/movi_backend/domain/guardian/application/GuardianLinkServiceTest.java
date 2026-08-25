package com.movi_backend.domain.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.notification.application.NotificationService;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.domain.notification.type.NotificationTemplate;
import com.movi_backend.global.audit.application.AuditLogService;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
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
class GuardianLinkServiceTest {

    private static final Long PROTECTEE_ID = 1L;
    private static final Long LINK_ID = 15L;
    private static final String RAW_PHONE = "010-1234-5678";
    private static final String NORMALIZED_PHONE = "01012345678";
    private static final String GUARDIAN_PHONE_HASH = "guardian-phone-hash";

    @Mock
    private GuardianLinkRepository guardianLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private GuardianLinkService guardianLinkService;

    @Test
    @DisplayName("보호자를 등록하면 확인 절차 없이 ACTIVE 연결이 생성된다")
    void 보호자를_등록하면_ACTIVE_연결이_생성된다() {
        // given
        givenRegisterFlow(false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.SENT);

        // when
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(PROTECTEE_ID, createRequest("CHILD"));

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.ACTIVE);
        assertThat(response.relation()).isEqualTo(GuardianRelation.CHILD.getDisplayName());
        assertThat(response.notificationSent()).isTrue();
    }

    @Test
    @DisplayName("보호자를 등록하면 등록 통보 SMS 이력이 생성된다")
    void 보호자를_등록하면_통보_SMS_이력이_생성된다() {
        // given
        givenRegisterFlow(false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.SENT);

        // when
        guardianLinkService.register(PROTECTEE_ID, createRequest("자녀"));

        // then
        final ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        then(notificationService).should().send(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(NotificationTemplate.GUARDIAN_LINK_REGISTERED);
        assertThat(captor.getValue().guardianLinkId()).isEqualTo(LINK_ID);
        assertThat(captor.getValue().normalizedPhone()).isEqualTo(NORMALIZED_PHONE);
    }

    @Test
    @DisplayName("SMS 발송에 실패해도 등록은 유지되고 안내 문구만 달라진다")
    void SMS_발송에_실패해도_등록은_유지된다() {
        // given
        givenRegisterFlow(false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.FAILED);

        // when
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(PROTECTEE_ID, createRequest("CHILD"));

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.notificationSent()).isFalse();
        assertThat(response.toVoiceMessage()).contains("알림 문자는 보내지 못했어요");
    }

    @Test
    @DisplayName("자기 전화번호를 보호자로 입력하면 거부한다")
    void 자기_전화번호를_보호자로_입력하면_거부한다() {
        // given
        final User protectee = user(PROTECTEE_ID, GUARDIAN_PHONE_HASH);
        given(userRepository.findById(PROTECTEE_ID)).willReturn(Optional.of(protectee));
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn(GUARDIAN_PHONE_HASH);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.register(PROTECTEE_ID, createRequest("CHILD")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELF_LINK_NOT_ALLOWED);
        then(guardianLinkRepository).should(never()).save(any(GuardianLink.class));
    }

    @Test
    @DisplayName("이미 ACTIVE인 보호자를 다시 등록하면 거부한다")
    void 이미_ACTIVE인_보호자를_다시_등록하면_거부한다() {
        // given
        givenRegisterFlow(true);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.register(PROTECTEE_ID, createRequest("CHILD")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED);
    }

    @Test
    @DisplayName("허용되지 않은 관계값은 거부한다")
    void 허용되지_않은_관계값은_거부한다() {
        // given
        given(userRepository.findById(PROTECTEE_ID))
                .willReturn(Optional.of(user(PROTECTEE_ID, "other-hash")));

        // when & then
        assertThatThrownBy(() -> guardianLinkService.register(PROTECTEE_ID, createRequest("이웃")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_GUARDIAN_RELATION);
    }

    private void givenRegisterFlow(final boolean alreadyLinked) {
        given(userRepository.findById(PROTECTEE_ID))
                .willReturn(Optional.of(user(PROTECTEE_ID, "protectee-hash")));
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn(GUARDIAN_PHONE_HASH);
        given(guardianLinkRepository.existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
                PROTECTEE_ID, GUARDIAN_PHONE_HASH, GuardianLinkStatus.ACTIVE))
                .willReturn(alreadyLinked);
        if (alreadyLinked) {
            return;
        }
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn("encrypted-phone");
        given(guardianLinkRepository.save(any(GuardianLink.class)))
                .willAnswer(invocation -> {
                    final GuardianLink saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", LINK_ID);
                    return saved;
                });
    }

    private GuardianLinkCreateRequest createRequest(final String relation) {
        return new GuardianLinkCreateRequest("김보호", RAW_PHONE, relation);
    }

    private User user(final Long id, final String phoneHash) {
        final User user = User.builder()
                .name("홍길동")
                .phone("encrypted")
                .phoneHash(phoneHash)
                .userType(UserType.SENIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
