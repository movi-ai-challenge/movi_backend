package com.movi_backend.domain.voice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class MockVoiceAnalysisClientTest {

    @Test
    @DisplayName("일반 발화를 분석하면 정상 이체 응답을 반환한다")
    void 일반_발화를_분석하면_정상_이체_응답을_반환한다() {
        // given
        final MockVoiceAnalysisClient client = new MockVoiceAnalysisClient();
        final VoiceAnalysisRequest request = VoiceAnalysisRequest.of(
                new ByteArrayResource(new byte[]{1}),
                "voice-123",
                15L,
                null,
                List.of()
        );

        // when
        final VoiceAnalysisResponse response = client.analyze(request);

        // then
        assertThat(response.requestId()).isEqualTo("voice-123");
        assertThat(response.voiceSessionId()).isEqualTo(15L);
        assertThat(response.intent()).isEqualTo(VoiceIntent.TRANSFER);
        assertThat(response.entities().amount()).isEqualTo(50_000L);
        assertThat(response.entities().recipient()).isEqualTo("엄마");
        assertThat(response.detectedMissingEntities()).isEmpty();
    }

    @Test
    @DisplayName("금액 후속 발화를 분석하면 이전 수취인을 채우지 않는다")
    void 금액_후속_발화를_분석하면_이전_수취인을_채우지_않는다() {
        // given
        final MockVoiceAnalysisClient client = new MockVoiceAnalysisClient();
        final VoiceAnalysisRequest request = VoiceAnalysisRequest.of(
                new ByteArrayResource(new byte[]{1}),
                "voice-124",
                15L,
                VoiceIntent.TRANSFER,
                List.of(VoiceSlot.AMOUNT)
        );

        // when
        final VoiceAnalysisResponse response = client.analyze(request);

        // then
        assertThat(response.entities().amount()).isEqualTo(50_000L);
        assertThat(response.entities().recipient()).isNull();
        assertThat(response.detectedMissingEntities()).containsExactly(VoiceSlot.RECIPIENT);
    }
}
