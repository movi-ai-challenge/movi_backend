package com.movi_backend.domain.voice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class HttpVoiceAnalysisClientTest {

    private static final String RESPONSE_JSON = """
            {
              "requestId": "voice-123",
              "voiceSessionId": 15,
              "transcript": "오만 원",
              "sttConfidence": 0.95,
              "intent": "TRANSFER",
              "intentConfidence": 0.95,
              "entities": {
                "amount": 50000,
                "recipient": null,
                "sourceAccountAlias": null,
                "bankName": null,
                "startDate": null,
                "endDate": null
              },
              "entityConfidences": {
                "amount": 0.95,
                "recipient": null,
                "sourceAccountAlias": null,
                "bankName": null,
                "startDate": null,
                "endDate": null
              },
              "detectedMissingEntities": ["RECIPIENT"],
              "processingMs": 100
            }
            """;

    @Test
    @DisplayName("Voice API를 호출하면 multipart 계약을 전송하고 검증된 응답을 반환한다")
    void Voice_API를_호출하면_multipart_계약을_전송하고_검증된_응답을_반환한다() {
        // given
        final RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpVoiceAnalysisClient client = new HttpVoiceAnalysisClient(
                builder.build(),
                new ObjectMapper(),
                new VoiceAnalysisResponseValidator()
        );
        final VoiceAnalysisRequest request = createRequest();
        server.expect(once(), requestTo("http://localhost:8000/internal/v1/voice/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("voice-123")))
                .andExpect(content().string(Matchers.containsString("expectedIntent")))
                .andExpect(content().string(Matchers.containsString("AMOUNT")))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        // when
        final VoiceAnalysisResponse response = client.analyze(request);

        // then
        assertThat(response.voiceSessionId()).isEqualTo(15L);
        assertThat(response.entities().amount()).isEqualTo(50_000L);
        assertThat(response.detectedMissingEntities()).containsExactly(VoiceSlot.RECIPIENT);
        server.verify();
    }

    @Test
    @DisplayName("Voice API가 실패하면 음성 인식 예외가 발생한다")
    void Voice_API가_실패하면_음성_인식_예외가_발생한다() {
        // given
        final RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpVoiceAnalysisClient client = new HttpVoiceAnalysisClient(
                builder.build(),
                new ObjectMapper(),
                new VoiceAnalysisResponseValidator()
        );
        final VoiceAnalysisRequest request = createRequest();
        server.expect(once(), requestTo("http://localhost:8000/internal/v1/voice/analyze"))
                .andRespond(withServerError());

        // when
        final Throwable thrown = catchThrowable(() -> client.analyze(request));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STT_FAILED);
        server.verify();
    }

    private VoiceAnalysisRequest createRequest() {
        return VoiceAnalysisRequest.of(
                new ByteArrayResource(new byte[]{1, 2, 3}) {
                    @Override
                    public String getFilename() {
                        return "voice.webm";
                    }
                },
                "voice-123",
                15L,
                VoiceIntent.TRANSFER,
                List.of(VoiceSlot.AMOUNT)
        );
    }
}
