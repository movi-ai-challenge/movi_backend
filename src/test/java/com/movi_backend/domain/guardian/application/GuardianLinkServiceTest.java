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
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
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
class GuardianLinkServiceTest {

    private static final Long PROTECTEE_ID = 1L;
    private static final Long LINK_ID = 15L;
    private static final String RAW_PHONE = "010-1234-5678";
    private static final String NORMALIZED_PHONE = "01012345678";
    private static final String ENCRYPTED_PHONE = "encrypted-phone";

    @Mock
    private GuardianLinkRepository guardianLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @InjectMocks
    private GuardianLinkService guardianLinkService;

    @Test
    @DisplayName("보호자를 등록하면 확인 절차 없이 바로 ACTIVE 연결이 생성된다")
    void 보호자를_등록하면_바로_ACTIVE_연결이_생성된다() {
        // given
        givenProtectee("protectee-hash");
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                PROTECTEE_ID, GuardianLinkStatus.ACTIVE)).willReturn(List.of());
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn("guardian-hash");
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn(ENCRYPTED_PHONE);
        givenSaveAssignsId();

        // when
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(PROTECTEE_ID, request("자녀"));

        // then
        assertThat(response.linkId()).isEqualTo(LINK_ID);
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.ACTIVE);
        assertThat(response.relation()).isEqualTo("자녀");
    }

    @Test
    @DisplayName("등록한 연결은 알림 대상 조회 조건(ACTIVE·암호화된 번호)을 만족한다")
    void 등록한_연결은_알림_대상_조건을_만족한다() {
        // given
        givenProtectee("protectee-hash");
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                PROTECTEE_ID, GuardianLinkStatus.ACTIVE)).willReturn(List.of());
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn("guardian-hash");
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn(ENCRYPTED_PHONE);
        givenSaveAssignsId();

        // when
        guardianLinkService.register(PROTECTEE_ID, request("자녀"));

        // then
        final ArgumentCaptor<GuardianLink> captor = ArgumentCaptor.forClass(GuardianLink.class);
        then(guardianLinkRepository).should().save(captor.capture());
        final GuardianLink saved = captor.getValue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getGuardianPhone()).isEqualTo(ENCRYPTED_PHONE);
        assertThat(saved.getAcceptedAt()).isNotNull();
        // 보호자는 Movi 회원이 아니어도 되므로 회원 계정은 비어 있다
        assertThat(saved.getGuardianUser()).isNull();
        // invite_token 은 NOT NULL + UNIQUE 라 값이 채워져야 한다
        assertThat(saved.getInviteToken()).isNotBlank();
    }

    @Test
    @DisplayName("자기 전화번호를 보호자로 등록하면 거부한다")
    void 자기_전화번호를_보호자로_등록하면_거부한다() {
        // given
        givenProtectee("same-hash");
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn("same-hash");

        // when & then
        assertThatThrownBy(() -> guardianLinkService.register(PROTECTEE_ID, request("자녀")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELF_LINK_NOT_ALLOWED);
        then(guardianLinkRepository).should(never()).save(any(GuardianLink.class));
    }

    @Test
    @DisplayName("이미 등록된 번호를 다시 등록하면 거부한다")
    void 이미_등록된_번호를_다시_등록하면_거부한다() {
        // given
        givenProtectee("protectee-hash");
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn("guardian-hash");
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                PROTECTEE_ID, GuardianLinkStatus.ACTIVE))
                .willReturn(List.of(existingLink()));
        given(sensitiveDataCrypto.decrypt(ENCRYPTED_PHONE)).willReturn(NORMALIZED_PHONE);

        // when & then
        assertThatThrownBy(() -> guardianLinkService.register(PROTECTEE_ID, request("자녀")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED);
        then(guardianLinkRepository).should(never()).save(any(GuardianLink.class));
    }

    @Test
    @DisplayName("전화번호가 아직 없는 계정도 보호자를 등록할 수 있다")
    void 전화번호가_없는_계정도_보호자를_등록할_수_있다() {
        // given — 카카오 가입 직후에는 phoneHash 가 비어 있다
        givenProtectee(null);
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                PROTECTEE_ID, GuardianLinkStatus.ACTIVE)).willReturn(List.of());
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn(ENCRYPTED_PHONE);
        givenSaveAssignsId();

        // when
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(PROTECTEE_ID, request("자녀"));

        // then
        assertThat(response.status()).isEqualTo(GuardianLinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("관계를 비워 두면 null로 저장한다")
    void 관계를_비워_두면_null로_저장한다() {
        // given
        givenProtectee("protectee-hash");
        given(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                PROTECTEE_ID, GuardianLinkStatus.ACTIVE)).willReturn(List.of());
        given(sensitiveDataCrypto.hash(NORMALIZED_PHONE)).willReturn("guardian-hash");
        given(sensitiveDataCrypto.encrypt(NORMALIZED_PHONE)).willReturn(ENCRYPTED_PHONE);
        givenSaveAssignsId();

        // when
        final GuardianLinkRegisterResponse response =
                guardianLinkService.register(PROTECTEE_ID, request("  "));

        // then
        assertThat(response.relation()).isNull();
    }

    private void givenProtectee(final String phoneHash) {
        given(userRepository.findById(PROTECTEE_ID)).willReturn(Optional.of(user(phoneHash)));
    }

    private void givenSaveAssignsId() {
        given(guardianLinkRepository.save(any(GuardianLink.class)))
                .willAnswer(invocation -> {
                    final GuardianLink saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", LINK_ID);
                    return saved;
                });
    }

    private GuardianLinkCreateRequest request(final String relation) {
        return new GuardianLinkCreateRequest("김보호", RAW_PHONE, relation);
    }

    private GuardianLink existingLink() {
        final GuardianLink link = GuardianLink.builder()
                .protecteeUser(user("protectee-hash"))
                .guardianName("김보호")
                .guardianPhone(ENCRYPTED_PHONE)
                .relation("자녀")
                .inviteToken("existing-token")
                .inviteExpiresAt(LocalDateTime.now())
                .build();
        link.activateWithoutInvite(LocalDateTime.now());
        return link;
    }

    private User user(final String phoneHash) {
        final User user = User.builder()
                .name("홍길동")
                .phoneHash(phoneHash)
                .userType(UserType.GENERAL)
                .build();
        ReflectionTestUtils.setField(user, "id", PROTECTEE_ID);
        return user;
    }
}
