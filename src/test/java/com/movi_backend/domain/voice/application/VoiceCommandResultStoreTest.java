package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("VoiceCommandResultStore 는")
class VoiceCommandResultStoreTest {

    private VoiceCommandResultStore store;
    private VoiceCommandResponse response;

    @BeforeEach
    void setUp() {
        store = new VoiceCommandResultStore();
        response = Mockito.mock(VoiceCommandResponse.class);
    }

    @Test
    @DisplayName("보관한 답을 주인에게 돌려준다")
    void returnsStoredResultToOwner() {
        store.store(1L, 100L, response, "2만원을 보낼까요?");

        final VoiceCommandResultStore.StoredResult stored = store.find(1L, 100L);

        assertThat(stored).isNotNull();
        assertThat(stored.voiceMessage()).isEqualTo("2만원을 보낼까요?");
        assertThat(stored.response()).isSameAs(response);
    }

    @Test
    @DisplayName("남의 세션은 들여다볼 수 없다")
    void hidesOtherUsersResult() {
        store.store(1L, 100L, response, "2만원을 보낼까요?");

        // 세션 번호는 순번이라, 옆 번호를 넣어보는 것만으로 남의 이체가 보이면 안 된다.
        assertThat(store.find(2L, 100L)).isNull();
    }

    @Test
    @DisplayName("보관한 적 없는 세션은 비어 있다")
    void returnsNullForUnknownSession() {
        assertThat(store.find(1L, 999L)).isNull();
    }

    @Test
    @DisplayName("대화가 끝난 세션은 지운다")
    void removesFinishedSession() {
        store.store(1L, 100L, response, "보냈어요.");
        store.remove(100L);

        assertThat(store.find(1L, 100L)).isNull();
    }

    @Test
    @DisplayName("오래된 답은 이어갈 의미가 없어 버린다")
    void evictsExpiredResult() {
        store.store(1L, 100L, response, "2만원을 보낼까요?");

        assertThat(store.find(1L, 100L)).isNotNull();

        // 보관 시각을 과거로 돌려 만료를 만든다. removeIf 가 도므로 변경 가능한 맵이어야 한다.
        final Map<Long, VoiceCommandResultStore.StoredResult> aged = new ConcurrentHashMap<>();
        aged.put(100L, new VoiceCommandResultStore.StoredResult(
                1L, response, "2만원을 보낼까요?", LocalDateTime.now().minusHours(1)
        ));
        ReflectionTestUtils.setField(store, "resultsBySession", aged);

        assertThat(store.find(1L, 100L)).isNull();
    }

    @Test
    @DisplayName("세션 번호가 없으면 보관하지 않는다")
    void ignoresMissingSessionId() {
        store.store(1L, null, response, "무언가");

        assertThat(store.find(1L, null)).isNull();
    }
}
