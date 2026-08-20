package com.movi_backend.domain.voice.client;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
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
                    .body(VoiceAnalysisResponse.class);
            return responseValidator.validate(request, response);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RestClientException | JacksonException exception) {
            throw new BusinessException(ErrorCode.STT_FAILED, exception.getClass().getSimpleName());
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
