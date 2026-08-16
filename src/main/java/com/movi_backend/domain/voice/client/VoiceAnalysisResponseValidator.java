package com.movi_backend.domain.voice.client;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class VoiceAnalysisResponseValidator {

    private static final BigDecimal MINIMUM_CONFIDENCE = BigDecimal.ZERO;
    private static final BigDecimal MAXIMUM_CONFIDENCE = BigDecimal.ONE;

    public VoiceAnalysisResponse validate(
            final VoiceAnalysisRequest request,
        final VoiceAnalysisResponse response
    ) {
        validateRequiredFields(request, response);
        validateRequiredConfidence(response.sttConfidence());
        validateRequiredConfidence(response.intentConfidence());
        validateEntityConfidences(response.entityConfidences());
        validateProcessingTime(response.processingMs());
        return response;
    }

    private void validateRequiredFields(
            final VoiceAnalysisRequest request,
            final VoiceAnalysisResponse response
    ) {
        if (response == null) {
            throw invalidResponse("응답 없음");
        }
        if (!Objects.equals(request.requestId(), response.requestId())) {
            throw invalidResponse("requestId 불일치");
        }
        if (!Objects.equals(request.voiceSessionId(), response.voiceSessionId())) {
            throw invalidResponse("voiceSessionId 불일치");
        }
        if (response.intent() == null) {
            throw invalidResponse("intent 누락");
        }
        if (response.transcript() == null || response.transcript().isBlank()) {
            throw invalidResponse("transcript 누락");
        }
        if (response.entities() == null || response.entityConfidences() == null) {
            throw invalidResponse("entity 객체 누락");
        }
        if (response.detectedMissingEntities() == null) {
            throw invalidResponse("detectedMissingEntities 누락");
        }
    }

    private void validateEntityConfidences(final VoiceEntityConfidences confidences) {
        validateConfidence(confidences.amount());
        validateConfidence(confidences.recipient());
        validateConfidence(confidences.sourceAccountAlias());
        validateConfidence(confidences.bankName());
        validateConfidence(confidences.startDate());
        validateConfidence(confidences.endDate());
    }

    private void validateRequiredConfidence(final BigDecimal confidence) {
        if (confidence == null) {
            throw invalidResponse("confidence 누락");
        }
        validateConfidence(confidence);
    }

    private void validateConfidence(final BigDecimal confidence) {
        if (confidence == null) {
            return;
        }
        if (confidence.compareTo(MINIMUM_CONFIDENCE) < 0
                || confidence.compareTo(MAXIMUM_CONFIDENCE) > 0) {
            throw invalidResponse("confidence 범위 오류");
        }
    }

    private void validateProcessingTime(final Integer processingMs) {
        if (processingMs == null || processingMs < 0) {
            throw invalidResponse("processingMs 범위 오류");
        }
    }

    private BusinessException invalidResponse(final String detailMessage) {
        return new BusinessException(ErrorCode.STT_FAILED, detailMessage);
    }
}
