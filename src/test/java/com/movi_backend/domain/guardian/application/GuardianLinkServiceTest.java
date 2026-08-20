package com.movi_backend.domain.guardian.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianInvitationResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkApprovalResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRequestResponse;
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
import java.time.LocalDateTime;
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
    private static final Long GUARDIAN_ID = 2L;
    private static final Long LINK_ID = 15L;
    private static final String RAW_PHONE = "010-1234-5678";
    private static final String NORMALIZED_PHONE = "01012345678";
    private static final String GUARDIAN_PHONE_HASH = "guardian-phone-hash";
    private static final String INVITE_TOKEN = "invite-token";

    @Mock
    private GuardianLinkRepository guardianLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GuardianInvitationService guardianInvitationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private GuardianLinkService guardianLinkService;

    @Test
    @DisplayName("보호자 등록을 요청하면 REQUESTED 연결이 생성된다")
    void 보호자_등록을_요청하면_REQUESTED_연결이_생성된다() {
        // given
        givenRequestFlow(false, false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.SENT);

        // when
        final GuardianLinkRequestResponse response =
                guardianLinkService.request(PROTECTEE_ID, createRequest("CHILD"));

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.REQUESTED);
        assertThat(response.relation()).isEqualTo(GuardianRelation.CHILD.getDisplayName());
        assertThat(response.invitationSent()).isTrue();
    }

    @Test
    @DisplayName("보호자 등록을 요청하면 초대 SMS 이력이 생성된다")
    void 보호자_등록을_요청하면_SMS_알림_이력이_생성된다() {
        // given
        givenRequestFlow(false, false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.SENT);

        // when
        guardianLinkService.request(PROTECTEE_ID, createRequest("자녀"));

        // then
        final ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        then(notificationService).should().send(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(NotificationTemplate.GUARDIAN_INVITE);
        assertThat(captor.getValue().guardianLinkId()).isEqualTo(LINK_ID);
        assertThat(captor.getValue().normalizedPhone()).isEqualTo(NORMALIZED_PHONE);
    }

    @Test
    @DisplayName("SMS 발송에 실패해도 연결 요청은 남고 안내 문구만 달라진다")
    void SMS_발송에_실패해도_요청은_유지된다() {
        // given
        givenRequestFlow(false, false);
        given(notificationService.send(any(NotificationRequest.class)))
                .willReturn(NotificationStatus.FAILED);

        // when
        final GuardianLinkRequestResponse response =
                guardianLinkService.request(PROTECTEE_ID, createRequest("CHILD"));

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.invitationSent()).isFalse();
        assertThat(response.toVoiceMessage()).contains("문자를 보내지 못했어요");
    }

    @Test
    @DisplayName("자기 전화번호를 보호자로 입력하면 거부한다")
    void 자기_전화번호를_보호자로_입력하면_거부한다() {
        // given
        final User protectee = user(PROTECTEE_ID, GUARDIAN_PHONE_HASH);
        given(userRepository.findById(PROTECTEE_ID)).willReturn(Optional.of(protectee));
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn(GUARDIAN_PHONE_HASH);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.request(PROTECTEE_ID, createRequest("CHILD")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELF_LINK_NOT_ALLOWED);
        then(guardianLinkRepository).should(never()).save(any(GuardianLink.class));
    }

    @Test
    @DisplayName("이미 ACTIVE인 보호자를 다시 등록하면 거부한다")
    void 이미_ACTIVE인_보호자를_다시_등록하면_거부한다() {
        // given
        givenRequestFlow(true, false);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.request(PROTECTEE_ID, createRequest("CHILD")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED);
    }

    @Test
    @DisplayName("살아 있는 초대가 있으면 중복 요청을 거부한다")
    void 이미_REQUESTED인_연결을_다시_요청하면_거부한다() {
        // given
        givenRequestFlow(false, true);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.request(PROTECTEE_ID, createRequest("CHILD")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_GUARDIAN_REQUEST);
    }

    @Test
    @DisplayName("허용되지 않은 관계값은 거부한다")
    void 허용되지_않은_관계값은_거부한다() {
        // given
        given(userRepository.findById(PROTECTEE_ID))
                .willReturn(Optional.of(user(PROTECTEE_ID, "other-hash")));

        // when & then
        assertThatThrownBy(() -> guardianLinkService.request(PROTECTEE_ID, createRequest("이웃")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_GUARDIAN_RELATION);
    }

    @Test
    @DisplayName("유효한 초대 토큰으로 요청 내용을 조회한다")
    void 유효한_초대_토큰으로_요청_내용을_조회한다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));

        // when
        final GuardianInvitationResponse response = guardianLinkService.findInvitation(INVITE_TOKEN);

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.protecteeName()).isEqualTo("홍길동");
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.REQUESTED);
    }

    @Test
    @DisplayName("만료된 초대 토큰은 조회할 수 없다")
    void 만료된_초대_토큰은_조회할_수_없다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().minusMinutes(1));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));

        // when & then
        assertThatThrownBy(() -> guardianLinkService.findInvitation(INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVITE_EXPIRED);
    }

    @Test
    @DisplayName("존재하지 않는 초대 토큰은 거부한다")
    void 존재하지_않는_초대_토큰은_거부한다() {
        // given
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> guardianLinkService.findInvitation(INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INVITE_TOKEN);
    }

    @Test
    @DisplayName("REQUESTED 요청을 승인하면 ACTIVE가 되고 승인 시각이 기록된다")
    void REQUESTED_요청을_승인하면_ACTIVE로_변경된다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        final User guardian = user(GUARDIAN_ID, "guardian-hash");
        given(userRepository.findById(GUARDIAN_ID)).willReturn(Optional.of(guardian));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));
        given(guardianLinkRepository.existsByProtecteeUserIdAndGuardianUserIdAndStatus(
                PROTECTEE_ID, GUARDIAN_ID, GuardianLinkStatus.ACTIVE)).willReturn(false);
        given(guardianLinkRepository.approveIfRequested(
                anyLong(), any(User.class), eq(GuardianLinkStatus.ACTIVE),
                eq(GuardianLinkStatus.REQUESTED), any(LocalDateTime.class))).willReturn(1);
        link.accept(guardian, LocalDateTime.now());
        given(guardianLinkRepository.findById(LINK_ID)).willReturn(Optional.of(link));

        // when
        final GuardianLinkApprovalResponse response =
                guardianLinkService.approve(GUARDIAN_ID, INVITE_TOKEN);

        // then
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.ACTIVE);
        assertThat(response.protecteeUserId()).isEqualTo(PROTECTEE_ID);
        assertThat(response.acceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("동일 요청에 동시에 승인하면 하나만 성공한다")
    void 동일_요청에_동시에_승인하면_하나만_성공한다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        given(userRepository.findById(GUARDIAN_ID))
                .willReturn(Optional.of(user(GUARDIAN_ID, "guardian-hash")));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));
        given(guardianLinkRepository.existsByProtecteeUserIdAndGuardianUserIdAndStatus(
                PROTECTEE_ID, GUARDIAN_ID, GuardianLinkStatus.ACTIVE)).willReturn(false);
        given(guardianLinkRepository.approveIfRequested(
                anyLong(), any(User.class), eq(GuardianLinkStatus.ACTIVE),
                eq(GuardianLinkStatus.REQUESTED), any(LocalDateTime.class))).willReturn(0);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.approve(GUARDIAN_ID, INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("이미 처리된 요청은 다시 승인할 수 없다")
    void 이미_ACTIVE인_요청을_다시_승인하면_거부한다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        link.accept(user(GUARDIAN_ID, "guardian-hash"), LocalDateTime.now());
        given(userRepository.findById(GUARDIAN_ID))
                .willReturn(Optional.of(user(GUARDIAN_ID, "guardian-hash")));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));

        // when & then
        assertThatThrownBy(() -> guardianLinkService.approve(GUARDIAN_ID, INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("자기 자신의 보호자로 연결하면 거부한다")
    void 자기_자신의_보호자로_연결하면_거부한다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        given(userRepository.findById(PROTECTEE_ID))
                .willReturn(Optional.of(user(PROTECTEE_ID, "protectee-hash")));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));

        // when & then
        assertThatThrownBy(() -> guardianLinkService.approve(PROTECTEE_ID, INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELF_LINK_NOT_ALLOWED);
    }

    @Test
    @DisplayName("동일한 피보호자-보호자 관계가 있으면 승인을 거부한다")
    void 동일한_피보호자_보호자_관계가_있으면_승인을_거부한다() {
        // given
        final GuardianLink link = requestedLink(LocalDateTime.now().plusHours(1));
        given(userRepository.findById(GUARDIAN_ID))
                .willReturn(Optional.of(user(GUARDIAN_ID, "guardian-hash")));
        given(guardianLinkRepository.findByInviteToken(INVITE_TOKEN)).willReturn(Optional.of(link));
        given(guardianLinkRepository.existsByProtecteeUserIdAndGuardianUserIdAndStatus(
                PROTECTEE_ID, GUARDIAN_ID, GuardianLinkStatus.ACTIVE)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.approve(GUARDIAN_ID, INVITE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED);
    }

    private void givenRequestFlow(final boolean alreadyLinked, final boolean invitationAlive) {
        given(userRepository.findById(PROTECTEE_ID))
                .willReturn(Optional.of(user(PROTECTEE_ID, "protectee-hash")));
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn(GUARDIAN_PHONE_HASH);
        given(guardianLinkRepository.existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
                PROTECTEE_ID, GUARDIAN_PHONE_HASH, GuardianLinkStatus.ACTIVE))
                .willReturn(alreadyLinked);
        if (alreadyLinked) {
            return;
        }
        given(guardianLinkRepository.existsLivingInvitation(
                eq(PROTECTEE_ID), eq(GUARDIAN_PHONE_HASH), eq(GuardianLinkStatus.REQUESTED),
                any(LocalDateTime.class))).willReturn(invitationAlive);
        if (invitationAlive) {
            return;
        }
        given(guardianInvitationService.generateInviteToken()).willReturn(INVITE_TOKEN);
        given(guardianInvitationService.calculateExpiresAt(any(LocalDateTime.class)))
                .willReturn(LocalDateTime.now().plusHours(24));
        given(guardianInvitationService.buildInviteUrl(INVITE_TOKEN))
                .willReturn("https://movi.example/guardian/invite?token=" + INVITE_TOKEN);
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

    private GuardianLink requestedLink(final LocalDateTime inviteExpiresAt) {
        final GuardianLink link = GuardianLink.builder()
                .protecteeUser(user(PROTECTEE_ID, "protectee-hash"))
                .guardianName("김보호")
                .guardianPhone("encrypted-phone")
                .guardianPhoneHash(GUARDIAN_PHONE_HASH)
                .relation(GuardianRelation.CHILD)
                .inviteToken(INVITE_TOKEN)
                .inviteExpiresAt(inviteExpiresAt)
                .build();
        ReflectionTestUtils.setField(link, "id", LINK_ID);
        return link;
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
