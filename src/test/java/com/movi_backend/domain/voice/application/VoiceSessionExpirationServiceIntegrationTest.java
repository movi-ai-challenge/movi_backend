package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceChannel;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class VoiceSessionExpirationServiceIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private VoiceSessionRepository voiceSessionRepository;

    @Autowired
    private VoiceSessionExpirationService expirationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("바깥 처리가 롤백되어도 세션 만료는 별도 트랜잭션으로 유지한다")
    void 바깥_처리가_롤백되어도_세션_만료는_별도_트랜잭션으로_유지한다() {
        // given
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final Long sessionId = transactionTemplate.execute(status -> {
            final User user = User.builder()
                    .name("만료 테스트 사용자")
                    .phone("encrypted-expiration-test-phone")
                    .userType(UserType.SENIOR)
                    .build();
            entityManager.persist(user);
            final VoiceSession session = VoiceSession.builder()
                    .user(user)
                    .channel(VoiceChannel.APP)
                    .build();
            session.clarify(VoiceIntent.TRANSFER, "{\"recipient\":\"엄마\"}", LocalDateTime.now());
            entityManager.persist(session);
            entityManager.flush();
            return session.getId();
        });

        // when
        transactionTemplate.executeWithoutResult(status -> {
            voiceSessionRepository.findById(sessionId).orElseThrow();
            expirationService.expire(sessionId, LocalDateTime.now());
            status.setRollbackOnly();
        });

        // then
        final VoiceSession expiredSession = voiceSessionRepository.findById(sessionId).orElseThrow();
        assertThat(expiredSession.getStatus()).isEqualTo(VoiceSessionStatus.EXPIRED);
        assertThat(expiredSession.getPendingSlots()).isNull();
        assertThat(expiredSession.getPendingIntent()).isNull();
        assertThat(expiredSession.getEndedAt()).isNotNull();
    }
}
