package com.movi_backend.domain.account.application;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 인증 state 저장소.
 *
 * <p><b>이 대조가 없으면 공격자가 자기 계좌를 피해자 계정에 연결할 수 있다.</b>
 * 공격자가 자신의 인가 코드를 피해자의 콜백으로 흘려보내면, 서버는 피해자 계정에
 * 공격자 계좌를 붙이게 된다. 그 뒤 피해자의 이체가 공격자 계좌에서 나가거나
 * 잔액이 노출된다.
 *
 * <p>발급한 state 는 <b>한 번만</b> 쓰인다. {@link #consume}은 검증과 동시에 제거하므로
 * 같은 코드를 재사용하는 시도는 실패한다.
 *
 * <p>MVP 범위라 메모리에 둔다. 서버가 여러 대가 되거나 재기동 중 인증이 끊기면 안 되는
 * 시점에는 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class OAuthStateStore {

    /** 인증 페이지 체류 시간을 고려한 유효시간 */
    private static final int STATE_TIMEOUT_MINUTES = 5;

    private static final int STATE_BYTES = 24;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> states = new ConcurrentHashMap<>();

    /** 사용자에게 발급할 state 를 만든다. */
    public String issue(final Long userId) {
        evictExpired();
        final byte[] buffer = new byte[STATE_BYTES];
        random.nextBytes(buffer);
        final String state = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        states.put(state, new Entry(userId, LocalDateTime.now().plusMinutes(STATE_TIMEOUT_MINUTES)));
        return state;
    }

    /**
     * state 를 검증하고 소유자를 돌려준다. 검증과 동시에 제거해 재사용을 막는다.
     *
     * @return 유효하면 사용자 ID, 없거나 만료됐으면 {@code null}
     */
    public Long consume(final String state) {
        if (state == null) {
            return null;
        }
        final Entry entry = states.remove(state);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(LocalDateTime.now())) {
            return null;
        }
        return entry.userId();
    }

    private void evictExpired() {
        final LocalDateTime now = LocalDateTime.now();
        states.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record Entry(Long userId, LocalDateTime expiresAt) {
        private boolean isExpired(final LocalDateTime now) {
            return !now.isBefore(this.expiresAt);
        }
    }
}
