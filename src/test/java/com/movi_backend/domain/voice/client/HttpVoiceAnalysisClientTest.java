package com.movi_backend.domain.voice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
import org.springframework.http.HttpStatus;
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

    /**
     * AI 는 FastAPI HTTPException 으로 내보내 계약 형태가 detail 안에 한 겹 감싸인다.
     * 실제 운영에서 돌아오는 모양이라 그대로 넣는다.
     */
    private static final String WRAPPED_ERROR_JSON = """
            {
              "detail": {
                "requestId": "voice-123",
                "error": {
                  "code": "%s",
                  "message": "internal detail",
                  "retryable": true
                }
              }
            }
            """;

    private BusinessException analyzeExpectingFailure(
            final HttpStatus status,
            final String contractCode
    ) {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final HttpVoiceAnalysisClient client = new HttpVoiceAnalysisClient(
                builder.build(),
                new ObjectMapper(),
                new VoiceAnalysisResponseValidator()
        );
        server.expect(once(), requestTo("http://localhost:8000/internal/v1/voice/analyze"))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(WRAPPED_ERROR_JSON.formatted(contractCode)));

        final Throwable thrown = catchThrowable(() -> client.analyze(createRequest()));

        server.verify();
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    @Test
    @DisplayName("형식이 잘못된 오디오는 인식 실패가 아니라 미디어 타입 오류로 알린다")
    void 형식_오류는_미디어_타입_오류로_알린다() {
        // 전부 STT_FAILED 로 뭉개면 사용자는 "다시 말씀해 주세요"만 듣고
        // 원인이 파일 형식이라는 것을 알 수 없다.
        final BusinessException thrown =
                analyzeExpectingFailure(HttpStatus.BAD_REQUEST, "UNSUPPORTED_AUDIO_FORMAT");

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    @DisplayName("너무 긴 오디오는 길이 초과로 알린다")
    void 긴_오디오는_길이_초과로_알린다() {
        final BusinessException thrown =
                analyzeExpectingFailure(HttpStatus.BAD_REQUEST, "AUDIO_TOO_LONG");

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.AUDIO_DURATION_EXCEEDED);
    }

    @Test
    @DisplayName("인식된 문장이 없으면 STT 실패로 알린다")
    void 빈_인식결과는_STT_실패다() {
        final BusinessException thrown =
                analyzeExpectingFailure(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_TRANSCRIPT");

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.STT_FAILED);
    }

    @Test
    @DisplayName("모르는 코드가 와도 STT 실패로 처리하고 죽지 않는다")
    void 모르는_코드는_STT_실패로_떨어진다() {
        // AI 가 새 코드를 추가해도 백엔드가 500 으로 무너지면 안 된다.
        final BusinessException thrown =
                analyzeExpectingFailure(HttpStatus.INTERNAL_SERVER_ERROR, "SOME_NEW_CODE");

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.STT_FAILED);
    }
}
