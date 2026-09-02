package com.movi_backend.domain.voice.client;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisErrorResponse;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "movi.voice", name = "client-type", havingValue = "http")
public class HttpVoiceAnalysisClient implements VoiceAnalysisClient {

    private static final String ANALYZE_PATH = "/internal/v1/voice/analyze";

    /** 계약 2.7 의 변환표. 표에 없는 코드는 STT_FAILED 로 떨어진다. */
    private static final Map<String, ErrorCode> ERROR_CODE_BY_CONTRACT_CODE = Map.of(
            "UNSUPPORTED_AUDIO_FORMAT", ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "AUDIO_TOO_LONG", ErrorCode.AUDIO_DURATION_EXCEEDED,
            "EMPTY_TRANSCRIPT", ErrorCode.STT_FAILED,
            "STT_PROVIDER_ERROR", ErrorCode.STT_FAILED,
            "VOICE_ANALYSIS_TIMEOUT", ErrorCode.STT_FAILED,
            "MODEL_INFERENCE_ERROR", ErrorCode.STT_FAILED
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final VoiceAnalysisResponseValidator responseValidator;

    public HttpVoiceAnalysisClient(
            @Qualifier("voiceRestClient") final RestClient restClient,
            final ObjectMapper objectMapper,
            final VoiceAnalysisResponseValidator responseValidator
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.responseValidator = responseValidator;
    }

    @Override
    public VoiceAnalysisResponse analyze(final VoiceAnalysisRequest request) {
        try {
            final VoiceAnalysisResponse response = restClient.post()
                    .uri(ANALYZE_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(createMultipartBody(request))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw toBusinessException(res);
                    })
                    .body(VoiceAnalysisResponse.class);
            return responseValidator.validate(request, response);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RestClientException | JacksonException exception) {
            throw new BusinessException(ErrorCode.STT_FAILED, exception.getClass().getSimpleName());
        }
    }


    /**
     * AI 가 내려준 내부 오류 코드를 백엔드 오류로 바꾼다.
     *
     * <p>계약({@code docs/ai-api-contract.md} 2.7)이 코드별 변환을 정해 두었다. 전부
     * {@code STT_FAILED} 로 뭉개면 형식이 잘못된 파일도 "음성 인식 실패"로 안내돼,
     * 사용자는 다시 말하기만 반복하고 원인에 닿지 못한다.
     *
     * <p>코드를 읽지 못하면 {@code STT_FAILED} 로 둔다. AI 가 새 코드를 추가해도
     * 백엔드가 죽지 않아야 한다.
     */
    private BusinessException toBusinessException(final ClientHttpResponse response) {
        final String code = readErrorCode(response);
        if (code == null) {
            return new BusinessException(ErrorCode.STT_FAILED, "UNPARSEABLE_ERROR_BODY");
        }
        final ErrorCode mapped = ERROR_CODE_BY_CONTRACT_CODE.getOrDefault(code, ErrorCode.STT_FAILED);
        return new BusinessException(mapped, code);
    }

    private String readErrorCode(final ClientHttpResponse response) {
        try {
            return objectMapper
                    .readValue(response.getBody(), VoiceAnalysisErrorResponse.class)
                    .resolveCode();
        } catch (final IOException exception) {
            return null;
        }
    }

    private MultiValueMap<String, Object> createMultipartBody(
            final VoiceAnalysisRequest request
    ) throws JacksonException {
        final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audio", request.audio());
        body.add("requestId", request.requestId());
        body.add("voiceSessionId", request.voiceSessionId().toString());
        addExpectedIntent(body, request);
        addExpectedSlots(body, request.expectedSlots());
        return body;
    }

    private void addExpectedIntent(
            final MultiValueMap<String, Object> body,
            final VoiceAnalysisRequest request
    ) {
        if (request.expectedIntent() == null) {
            return;
        }
        body.add("expectedIntent", request.expectedIntent().name());
    }

    private void addExpectedSlots(
            final MultiValueMap<String, Object> body,
            final List<?> expectedSlots
    ) throws JacksonException {
        if (expectedSlots.isEmpty()) {
            return;
        }
        body.add("expectedSlots", objectMapper.writeValueAsString(expectedSlots));
    }
}
