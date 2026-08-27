package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.transfer.application.model.TransferConfirmation;
import com.movi_backend.domain.transfer.config.TransferProperties;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferConfirmationStoreTest {

    private static final Long USER_ID = 3L;
    private static final Long OTHER_USER_ID = 4L;
    private static final Long ACCOUNT_ID = 12L;
    private static final Long RECIPIENT_ID = 8L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    private final TransferConfirmationStore store = new TransferConfirmationStore(
            new TransferProperties(1L, 1_000_000L, 3_000_000L, 5)
    );

    @Test
    @DisplayName("검토를 저장하면 만료 시각이 붙은 확인 ID를 발급한다")
    void 검토를_저장하면_만료_시각이_붙은_확인_ID를_발급한다() {
        // when
        final TransferConfirmation confirmation = issue();

        // then
        assertThat(confirmation.confirmationId()).isNotBlank();
        assertThat(confirmation.amount()).isEqualTo(50_000L);
        assertThat(confirmation.expiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(confirmation.idempotencyKey()).isNull();
    }

    @Test
    @DisplayName("확인을 멱등성 키에 묶으면 같은 키의 재시도는 계속 통과한다")
    void 확인을_멱등성_키에_묶으면_같은_키의_재시도는_계속_통과한다() {
        // given
        final TransferConfirmation confirmation = issue();
        final String idempotencyKey = UUID.randomUUID().toString();

        // when
        final TransferConfirmation first = bind(confirmation, idempotencyKey);
        final TransferConfirmation retried = bind(confirmation, idempotencyKey);

        // then
        assertThat(first).isNotNull();
        assertThat(first.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(retried).isNotNull();
    }

    @Test
    @DisplayName("같은 확인을 다른 멱등성 키로 실행하려 하면 거부한다")
    void 같은_확인을_다른_멱등성_키로_실행하려_하면_거부한다() {
        // given — 사용자가 실행 버튼을 두 번 눌러 키가 새로 만들어진 상황
        final TransferConfirmation confirmation = issue();
        bind(confirmation, UUID.randomUUID().toString());

        // when
        final TransferConfirmation rebound = bind(confirmation, UUID.randomUUID().toString());

        // then
        assertThat(rebound).isNull();
    }

    @Test
    @DisplayName("만료된 확인은 실행할 수 없다")
    void 만료된_확인은_실행할_수_없다() {
        // given
        final TransferConfirmation confirmation = issue();

        // when
        final TransferConfirmation bound = store.bindIdempotencyKey(
                confirmation.confirmationId(),
                USER_ID,
                UUID.randomUUID().toString(),
                NOW.plusMinutes(5)
        );

        // then
        assertThat(bound).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 확인 ID로는 실행할 수 없다")
    void 다른_사용자의_확인_ID로는_실행할_수_없다() {
        // given
        final TransferConfirmation confirmation = issue();

        // when
        final TransferConfirmation bound = store.bindIdempotencyKey(
                confirmation.confirmationId(),
                OTHER_USER_ID,
                UUID.randomUUID().toString(),
                NOW
        );

        // then
        assertThat(bound).isNull();
    }

    @Test
    @DisplayName("제거한 확인은 다시 실행할 수 없다")
    void 제거한_확인은_다시_실행할_수_없다() {
        // given
        final TransferConfirmation confirmation = issue();
        store.remove(confirmation.confirmationId());

        // when
        final TransferConfirmation bound = bind(confirmation, UUID.randomUUID().toString());

        // then
        assertThat(bound).isNull();
    }

    @Test
    @DisplayName("없는 확인 ID는 실행할 수 없다")
    void 없는_확인_ID는_실행할_수_없다() {
        // when
        final TransferConfirmation bound = store.bindIdempotencyKey(
                UUID.randomUUID().toString(),
                USER_ID,
                UUID.randomUUID().toString(),
                NOW
        );

        // then
        assertThat(bound).isNull();
    }

    private TransferConfirmation issue() {
        return store.issue(USER_ID, ACCOUNT_ID, RECIPIENT_ID, 50_000L, NOW);
    }

    private TransferConfirmation bind(
            final TransferConfirmation confirmation,
            final String idempotencyKey
    ) {
        return store.bindIdempotencyKey(
                confirmation.confirmationId(),
                USER_ID,
                idempotencyKey,
                NOW
        );
    }
}
