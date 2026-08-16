package com.movi_backend.domain.voice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntities;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class VoiceAnalysisResponseValidatorTest {

    private static final String REQUEST_ID = "voice-123";
    private static final Long SESSION_ID = 15L;
    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.95");

    private final VoiceAnalysisResponseValidator validator =
            new VoiceAnalysisResponseValidator();

    @Test
    @DisplayName("계약에 맞는 Voice 응답을 검증하면 원본 응답을 반환한다")
    void 계약에_맞는_Voice_응답을_검증하면_원본_응답을_반환한다() {
        // given
        final VoiceAnalysisRequest request = createRequest();
        final VoiceAnalysisResponse response = createResponse(
                SESSION_ID,
                HIGH_CONFIDENCE
        );

        // when
        final VoiceAnalysisResponse validated = validator.validate(request, response);

        // then
        assertThat(validated).isSameAs(response);
    }

    @Test
    @DisplayName("응답의 세션 ID가 다르면 음성 인식 예외가 발생한다")
    void 응답의_세션_ID가_다르면_음성_인식_예외가_발생한다() {
        // given
        final VoiceAnalysisRequest request = createRequest();
        final VoiceAnalysisResponse response = createResponse(
                999L,
                HIGH_CONFIDENCE
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STT_FAILED);
    }

    @Test
    @DisplayName("응답의 신뢰도가 범위를 벗어나면 음성 인식 예외가 발생한다")
    void 응답의_신뢰도가_범위를_벗어나면_음성_인식_예외가_발생한다() {
        // given
        final VoiceAnalysisRequest request = createRequest();
        final VoiceAnalysisResponse response = createResponse(
                SESSION_ID,
                new BigDecimal("1.01")
        );

        // when
        final Throwable thrown = catchThrowable(() -> validator.validate(request, response));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.STT_FAILED);
    }

    private VoiceAnalysisRequest createRequest() {
        return VoiceAnalysisRequest.of(
                new ByteArrayResource(new byte[]{1, 2, 3}),
                REQUEST_ID,
                SESSION_ID,
                null,
                List.of()
        );
    }

    private VoiceAnalysisResponse createResponse(
            final Long sessionId,
            final BigDecimal sttConfidence
    ) {
        return VoiceAnalysisResponse.of(
                REQUEST_ID,
                sessionId,
                "엄마한테 오만 원 보내줘",
                sttConfidence,
                VoiceIntent.TRANSFER,
                HIGH_CONFIDENCE,
                VoiceEntities.transfer(50_000L, "엄마", null),
                VoiceEntityConfidences.transfer(HIGH_CONFIDENCE, HIGH_CONFIDENCE, null),
                List.of(),
                100
        );
    }
}
