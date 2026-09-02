package com.movi_backend.domain.guardian.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.type.NotificationChannel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuardianLinkRepository guardianLinkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("보호자가 미가입이어도 피보호자는 자기 이체로 나간 알림을 볼 수 있다")
    void 미가입_보호자에게_간_알림도_피보호자에게_보인다() {
        // given - 초대를 수락하지 않은 보호자다. notification.user 가 null 인 상태다.
        final User protectee = saveUser("피보호자", "protectee-hash-1");
        final GuardianLink link = saveLink(protectee, "미가입보호자");
        notificationRepository.save(Notification.builder()
                .user(null)
                .guardianLink(link)
                .channel(NotificationChannel.SMS)
                .templateCode("RISKY_TRANSFER_ALERT")
                .targetPhone("encrypted-guardian-phone")
                .payload("{\"riskLevel\":\"MEDIUM\"}")
                .build());

        // when
        final Page<Notification> found = notificationRepository.findMine(
                protectee.getId(), pageable());

        // then - 수신자 기준으로만 걸렀다면 여기서 0건이 나온다.
        assertThat(found.getTotalElements()).isEqualTo(1);
        assertThat(found.getContent()).singleElement().satisfies(notification ->
                assertThat(notification.getGuardianLink().getGuardianName())
                        .isEqualTo("미가입보호자"));
    }

    @Test
    @DisplayName("보호자로 가입한 사용자는 자기가 받은 알림을 볼 수 있다")
    void 가입한_보호자는_받은_알림을_본다() {
        // given
        final User protectee = saveUser("피보호자", "protectee-hash-2");
        final User guardian = saveUser("보호자", "guardian-hash-2");
        final GuardianLink link = saveLink(protectee, "가입보호자");
        link.accept(guardian, LocalDateTime.now());
        notificationRepository.save(Notification.builder()
                .user(guardian)
                .guardianLink(link)
                .channel(NotificationChannel.SMS)
                .templateCode("BLOCKED_TRANSFER_ALERT")
                .targetPhone("encrypted-guardian-phone")
                .payload("{\"riskLevel\":\"HIGH\"}")
                .build());

        // when
        final Page<Notification> found = notificationRepository.findMine(
                guardian.getId(), pageable());

        // then
        assertThat(found.getTotalElements()).isEqualTo(1);
        assertThat(found.getContent()).singleElement().satisfies(notification ->
                assertThat(notification.getTemplateCode()).isEqualTo("BLOCKED_TRANSFER_ALERT"));
    }

    @Test
    @DisplayName("남의 알림은 보이지 않는다")
    void 남의_알림은_보이지_않는다() {
        // given
        final User protectee = saveUser("피보호자", "protectee-hash-3");
        final User outsider = saveUser("남", "outsider-hash-3");
        final GuardianLink link = saveLink(protectee, "보호자");
        notificationRepository.save(Notification.builder()
                .user(null)
                .guardianLink(link)
                .channel(NotificationChannel.SMS)
                .templateCode("RISKY_TRANSFER_ALERT")
                .targetPhone("encrypted-guardian-phone")
                .payload("{\"riskLevel\":\"MEDIUM\"}")
                .build());

        // when
        final Page<Notification> found = notificationRepository.findMine(
                outsider.getId(), pageable());

        // then
        assertThat(found.getTotalElements()).isZero();
    }

    private User saveUser(final String name, final String phoneHash) {
        return userRepository.save(User.builder()
                .name(name)
                .phone("encrypted-" + phoneHash)
                .phoneHash(phoneHash)
                .userType(UserType.GENERAL)
                .build());
    }

    private GuardianLink saveLink(final User protectee, final String guardianName) {
        return guardianLinkRepository.save(GuardianLink.builder()
                .protecteeUser(protectee)
                .guardianName(guardianName)
                .guardianPhone("encrypted-guardian-phone")
                .relation("자녀")
                // invite_token·invite_expires_at 은 not null 이다. 초대 흐름을 타지 않는
                // 테스트라도 값을 채워야 저장된다.
                .inviteToken("invite-token-" + guardianName)
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                // permission_scope 는 JSON 컬럼이다. 이 테스트는 권한 범위를 보지 않으므로 비운다.
                .build());
    }

    private PageRequest pageable() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
    }
}
