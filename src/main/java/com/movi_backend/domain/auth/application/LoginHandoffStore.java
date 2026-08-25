package com.movi_backend.domain.auth.application;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 로그인 결과를 프런트로 넘기기 위한 일회성 교환 코드 저장소.
 *
 * <p><b>토큰을 URL 쿼리에 실어 보내면 안 된다.</b> 리다이렉트 주소는 브라우저 기록,
 * 프런트 호스트의 액세스 로그, 그 페이지가 외부 리소스를 부를 때의 {@code Referer} 헤더에
 * 남는다. Refresh 토큰은 유효기간이 길어 한 번 새면 오래 쓰인다.
 *
 * <p>그래서 리다이렉트에는 이 코드만 싣고, 프런트가 한 번 교환해 토큰을 본문으로 받는다.
 * <b>토큰은 교환 시점에 발급</b>하므로 그 전까지는 존재하지도 않는다. 코드가 새어도
 * 60초 안에, 아직 교환되지 않았을 때만 쓸 수 있다.
 *
 * <p>{@link #consume}은 검증과 동시에 제거한다. 같은 코드를 두 번 쓰는 시도는 실패한다.
 *
 * <p>MVP 범위라 메모리에 둔다. 서버가 여러 대가 되면 리다이렉트를 받은 서버와 교환을 받는
 * 서버가 달라져 로그인이 실패하므로, 그 시점에는 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class LoginHandoffStore {

    /**
     * 교환까지 걸리는 시간은 리다이렉트 직후 한 번의 요청뿐이라 짧게 잡는다.
     * 길게 두면 새어 나간 코드가 쓰일 수 있는 시간만 늘어난다.
     */
    private static final int HANDOFF_TIMEOUT_SECONDS = 60;

    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> handoffs = new ConcurrentHashMap<>();

    /** 로그인에 성공한 사용자에게 발급할 교환 코드를 만든다. */
    public String issue(final Long userId, final boolean newUser) {
        evictExpired();
        final byte[] buffer = new byte[CODE_BYTES];
        random.nextBytes(buffer);
        final String code = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        handoffs.put(code, new Entry(
                userId,
                newUser,
                LocalDateTime.now().plusSeconds(HANDOFF_TIMEOUT_SECONDS)
        ));
        return code;
    }

    /**
     * 코드를 검증하고 로그인 대상을 돌려준다. 검증과 동시에 제거해 재사용을 막는다.
     *
     * @return 유효하면 로그인 대상, 없거나 만료됐으면 {@code null}
     */
    public Handoff consume(final String code) {
        evictExpired();
        if (code == null || code.isBlank()) {
            return null;
        }
        final Entry entry = handoffs.remove(code);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(LocalDateTime.now())) {
            return null;
        }
        return new Handoff(entry.userId(), entry.newUser());
    }

    private void evictExpired() {
        final LocalDateTime now = LocalDateTime.now();
        handoffs.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /** 교환에 성공한 로그인 대상. */
    public record Handoff(Long userId, boolean newUser) {
    }

    private record Entry(Long userId, boolean newUser, LocalDateTime expiresAt) {

        private boolean isExpired(final LocalDateTime now) {
            return !now.isBefore(expiresAt);
        }
    }
}
