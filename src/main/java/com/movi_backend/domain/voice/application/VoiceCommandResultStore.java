package com.movi_backend.domain.voice.application;

import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 음성 세션의 마지막 응답을 잠시 들고 있는다.
 *
 * <p>스트리밍 응답은 <b>마지막 한 프레임이 도착해야만</b> 성공한다. 그 프레임을 놓치면
 * 답이 서버에 멀쩡히 있어도 사용자는 알 방법이 없다 — 화면에는 "잠시 문제가 생겼어요"만
 * 남고, 확인 질문은 영영 들리지 않는다. 실제로 이 지점에서 이체가 계속 멈췄다.
 *
 * <p>전달이 실패할 수 있다는 전제를 받아들이고, <b>다시 물어볼 수 있게</b> 한다. 여기
 * 담아 두면 연결이 끊긴 뒤에도 클라이언트가 조회해 이어갈 수 있다.
 *
 * <p>메모리에만 둔다. 확인 정보 자체가 이미 그렇고({@code TransferConfirmationStore}),
 * 이 값은 잠깐 뒤 쓸모가 없어진다. 잃어버려도 다시 말하면 그만이다.
 */
@Component
public class VoiceCommandResultStore {

    /** 이보다 오래된 답은 이어갈 의미가 없다. 확인 유효시간보다 넉넉히 잡는다. */
    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final Map<Long, StoredResult> resultsBySession = new ConcurrentHashMap<>();

    /** 세션의 마지막 응답. 없으면 {@code null} */
    public record StoredResult(
            Long userId,
            VoiceCommandResponse response,
            String voiceMessage,
            LocalDateTime storedAt
    ) {
    }

    public void store(
            final Long userId,
            final Long voiceSessionId,
            final VoiceCommandResponse response,
            final String voiceMessage
    ) {
        if (userId == null || voiceSessionId == null || response == null) {
            return;
        }
        evictExpired();
        resultsBySession.put(
                voiceSessionId,
                new StoredResult(userId, response, voiceMessage, LocalDateTime.now())
        );
    }

    /**
     * 세션의 마지막 응답을 꺼낸다.
     *
     * <p>남의 세션을 들여다볼 수 없도록 소유자를 함께 확인한다. 세션 번호는 순번이라
     * 옆 번호를 넣어보는 것만으로 다른 사람의 이체 내용을 볼 수 있으면 안 된다.
     *
     * @return 저장된 응답이 없거나 소유자가 다르면 {@code null}
     */
    public StoredResult find(final Long userId, final Long voiceSessionId) {
        // ConcurrentHashMap 은 null 키에 예외를 던진다. 조회 실패로 다루면 될 일이다.
        if (userId == null || voiceSessionId == null) {
            return null;
        }
        evictExpired();
        final StoredResult stored = resultsBySession.get(voiceSessionId);
        if (stored == null || !stored.userId().equals(userId)) {
            return null;
        }
        return stored;
    }

    /** 대화가 끝난 세션은 들고 있을 이유가 없다. */
    public void remove(final Long voiceSessionId) {
        if (voiceSessionId == null) {
            return;
        }
        resultsBySession.remove(voiceSessionId);
    }

    private void evictExpired() {
        final LocalDateTime threshold = LocalDateTime.now().minus(RETENTION);
        resultsBySession.entrySet()
                .removeIf(entry -> entry.getValue().storedAt().isBefore(threshold));
    }
}
