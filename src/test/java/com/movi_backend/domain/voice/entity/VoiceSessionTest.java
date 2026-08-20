package com.movi_backend.domain.voice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.voice.type.VoiceChannel;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoiceSessionTest {

    private static final String SLOTS = "{\"recipient\":\"엄마\",\"amount\":null}";

    @Test
    @DisplayName("만료 시각과 같은 순간이면 만료로 판정한다")
    void 만료_시각_경계는_만료로_본다() {
        // given
        final VoiceSession session = createSession();

        // when
        final LocalDateTime boundary = session.getExpiresAt();

        // then
        assertThat(session.isExpired(boundary)).isTrue();
        assertThat(session.isExpired(boundary.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("확인 대기로 전이하면 의도를 함께 보관한다")
    void 확인_대기는_의도를_보관한다() {
        // given
        final VoiceSession session = createSession();
        final LocalDateTime now = LocalDateTime.now();

        // when
        session.awaitConfirmation(VoiceIntent.TRANSFER, SLOTS, now);

        // then
        assertThat(session.getStatus()).isEqualTo(VoiceSessionStatus.AWAITING_CONFIRMATION);
        assertThat(session.getPendingIntent()).isEqualTo(VoiceIntent.TRANSFER);
        assertThat(session.getPendingSlots()).isEqualTo(SLOTS);
    }

    @Test
    @DisplayName("확인 대기를 거치지 않고 처리를 시작하면 예외가 발생한다")
    void 확인_없이_처리를_시작할_수_없다() {
        // given
        final VoiceSession session = createSession();
        final LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> session.startProcessing(now))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION_STATE);
    }

    @Test
    @DisplayName("완료된 세션은 어떤 상태로도 전이하지 않는다")
    void 완료된_세션은_전이하지_않는다() {
        // given
        final VoiceSession session = createSession();
        final LocalDateTime now = LocalDateTime.now();
        session.awaitConfirmation(VoiceIntent.TRANSFER, SLOTS, now);
        session.startProcessing(now);
        session.complete(now);

        // when & then
        assertThatThrownBy(() -> session.cancel(now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> session.startProcessing(now))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("세션을 종료하면 보관하던 슬롯을 전부 폐기한다")
    void 종료하면_슬롯을_폐기한다() {
        // given
        final VoiceSession session = createSession();
        final LocalDateTime now = LocalDateTime.now();
        session.clarify(VoiceIntent.TRANSFER, SLOTS, now);

        // when
        session.expire(now);

        // then
        assertThat(session.getPendingSlots()).isNull();
        assertThat(session.getPendingIntent()).isNull();
        assertThat(session.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("같은 슬롯을 세 번 되물으면 재질문 한도를 초과한다")
    void 재질문_한도를_초과한다() {
        // given
        final VoiceSession session = createSession();
        final LocalDateTime now = LocalDateTime.now();

        // when
        session.clarify(VoiceIntent.TRANSFER, SLOTS, now);
        session.clarify(VoiceIntent.TRANSFER, SLOTS, now);
        session.clarify(VoiceIntent.TRANSFER, SLOTS, now);

        // then
        assertThat(session.getRetryCount()).isEqualTo(VoiceSession.MAX_RETRY_COUNT);
        assertThat(session.isRetryExceeded()).isTrue();
    }

    private VoiceSession createSession() {
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted-phone")
                .userType(UserType.VISUALLY_IMPAIRED)
                .build();
        return VoiceSession.builder()
                .user(user)
                .channel(VoiceChannel.APP)
                .build();
    }
}
