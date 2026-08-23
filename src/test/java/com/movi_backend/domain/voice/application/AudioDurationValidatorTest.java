package com.movi_backend.domain.voice.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AudioDurationValidatorTest {

    private final AudioDurationValidator validator = new AudioDurationValidator();

    @Test
    @DisplayName("15초 WAV 파일은 허용한다")
    void 최대_길이_WAV_파일은_허용한다() {
        final MockMultipartFile audio = audio("voice.wav", "audio/wav", wav(15.0));

        assertThatCode(() -> validator.validate(audio, "audio/wav"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("15초를 초과한 WAV 파일은 거부한다")
    void 최대_길이를_초과한_WAV_파일은_거부한다() {
        final MockMultipartFile audio = audio("voice.wav", "audio/wav", wav(15.01));

        assertError(audio, "audio/wav", ErrorCode.AUDIO_DURATION_EXCEEDED);
    }

    @Test
    @DisplayName("15초 WebM 파일은 허용한다")
    void 최대_길이_WebM_파일은_허용한다() {
        final MockMultipartFile audio = audio("voice.webm", "audio/webm", webm(15.0));

        assertThatCode(() -> validator.validate(audio, "audio/webm"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("15초를 초과한 WebM 파일은 거부한다")
    void 최대_길이를_초과한_WebM_파일은_거부한다() {
        final MockMultipartFile audio = audio("voice.webm", "audio/webm", webm(15.01));

        assertError(audio, "audio/webm", ErrorCode.AUDIO_DURATION_EXCEEDED);
    }

    @Test
    @DisplayName("재생 시간 메타데이터가 없는 손상 WebM 파일은 거부한다")
    void 재생_시간이_없는_WebM_파일은_거부한다() {
        final byte[] header = element(0x1A45DFA3L, element(0x4282L, ascii("webm")));
        final byte[] segment = element(0x18538067L, element(0x1549A966L, new byte[0]));
        final MockMultipartFile audio = audio(
                "voice.webm",
                "audio/webm",
                concat(header, segment)
        );

        assertError(audio, "audio/webm", ErrorCode.AUDIO_DURATION_INVALID);
    }

    @Test
    @DisplayName("WAV로 위장한 파일은 거부한다")
    void WAV로_위장한_파일은_거부한다() {
        final MockMultipartFile audio = audio(
                "voice.wav",
                "audio/wav",
                new byte[]{1, 2, 3, 4}
        );

        assertError(audio, "audio/wav", ErrorCode.AUDIO_DURATION_INVALID);
    }

    private void assertError(
            final MockMultipartFile audio,
            final String contentType,
            final ErrorCode expected
    ) {
        assertThatThrownBy(() -> validator.validate(audio, contentType))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private MockMultipartFile audio(
            final String filename,
            final String contentType,
            final byte[] bytes
    ) {
        return new MockMultipartFile("audio", filename, contentType, bytes);
    }

    private byte[] wav(final double durationSeconds) {
        final int sampleRate = 8_000;
        final int channels = 1;
        final int bitsPerSample = 8;
        final int byteRate = sampleRate * channels * bitsPerSample / 8;
        final int dataSize = (int) Math.round(byteRate * durationSeconds);
        final ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(ascii("RIFF"));
        buffer.putInt(36 + dataSize);
        buffer.put(ascii("WAVE"));
        buffer.put(ascii("fmt "));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) (channels * bitsPerSample / 8));
        buffer.putShort((short) bitsPerSample);
        buffer.put(ascii("data"));
        buffer.putInt(dataSize);
        buffer.put(new byte[dataSize]);
        return buffer.array();
    }

    private byte[] webm(final double durationSeconds) {
        final byte[] docType = element(0x4282L, ascii("webm"));
        final byte[] header = element(0x1A45DFA3L, docType);
        final byte[] timecodeScale = element(0x2AD7B1L, unsigned(1_000_000L, 3));
        final byte[] duration = element(
                0x4489L,
                ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(durationSeconds * 1_000).array()
        );
        final byte[] info = element(0x1549A966L, concat(timecodeScale, duration));
        final byte[] segment = element(0x18538067L, info);
        return concat(header, segment);
    }

    private byte[] element(final long id, final byte[] data) {
        if (data.length > 126) {
            throw new IllegalArgumentException("테스트 EBML 요소가 너무 큼");
        }
        return concat(id(id), new byte[]{(byte) (0x80 | data.length)}, data);
    }

    private byte[] id(final long value) {
        int length = 1;
        while ((value >>> (length * 8)) != 0) {
            length++;
        }
        return unsigned(value, length);
    }

    private byte[] unsigned(final long value, final int length) {
        final byte[] bytes = new byte[length];
        for (int index = length - 1; index >= 0; index--) {
            bytes[index] = (byte) (value >>> (8 * (length - index - 1)));
        }
        return bytes;
    }

    private byte[] ascii(final String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] concat(final byte[]... arrays) {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] array : arrays) {
                output.write(array);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
