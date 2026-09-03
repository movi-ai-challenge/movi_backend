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
    @DisplayName("15초 MP4 버전 0 파일은 허용한다")
    void 최대_길이_MP4_버전_0_파일은_허용한다() {
        final MockMultipartFile audio = audio("voice.mp4", "audio/mp4", mp4(15.0, 0));

        assertThatCode(() -> validator.validate(audio, "audio/mp4"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("15초 M4A 버전 1 파일은 허용한다")
    void 최대_길이_M4A_버전_1_파일은_허용한다() {
        final MockMultipartFile audio = audio("voice.m4a", "audio/x-m4a", mp4(15.0, 1));

        assertThatCode(() -> validator.validate(audio, "audio/x-m4a"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("15초를 초과한 MP4 파일은 거부한다")
    void 최대_길이를_초과한_MP4_파일은_거부한다() {
        final MockMultipartFile audio = audio("voice.mp4", "audio/mp4", mp4(15.01, 0));

        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_EXCEEDED);
    }

    @Test
    @DisplayName("iPhone 이 만드는 조각난 MP4 는 재생 시간이 0이어도 허용한다")
    void 조각난_MP4_는_허용한다() {
        final MockMultipartFile audio = audio("voice.mp4", "audio/mp4", fragmentedMp4());

        assertThatCode(() -> validator.validate(audio, "audio/mp4"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("조각나지 않았는데 재생 시간이 0인 MP4 파일은 거부한다")
    void 재생_시간이_0인_일반_MP4_는_거부한다() {
        final MockMultipartFile audio = audio("voice.mp4", "audio/mp4", mp4(0.0, 0));

        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_INVALID);
    }

    @Test
    @DisplayName("재생 시간 메타데이터가 없는 손상 MP4 파일은 거부한다")
    void 재생_시간이_없는_MP4_파일은_거부한다() {
        final byte[] fileType = mp4Box("ftyp", concat(ascii("M4A "), new byte[4]));
        final byte[] movie = mp4Box("moov", new byte[0]);
        final MockMultipartFile audio = audio(
                "voice.mp4",
                "audio/mp4",
                concat(fileType, movie)
        );

        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_INVALID);
    }

    @Test
    @DisplayName("파일 범위를 벗어난 box 크기를 가진 MP4 파일은 거부한다")
    void box_크기가_잘못된_MP4_파일은_거부한다() {
        final ByteBuffer invalid = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        invalid.putInt(1_024);
        invalid.put(ascii("ftyp"));
        invalid.put(ascii("M4A "));
        invalid.putInt(0);
        final MockMultipartFile audio = audio(
                "voice.mp4",
                "audio/mp4",
                invalid.array()
        );

        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_INVALID);
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

    /**
     * iPhone Safari 가 만드는 조각난 MP4 를 흉내낸다.
     *
     * <p>{@code mvex} 가 있고 {@code mvhd} 재생 시간이 0인 형태다.
     */
    private byte[] fragmentedMp4() {
        final byte[] fileType = mp4Box(
                "ftyp",
                concat(ascii("M4A "), new byte[4], ascii("M4A "), ascii("isom"))
        );
        final byte[] movieHeader = mp4Box("mvhd", mp4MovieHeader(0, 1_000, 0L));
        final byte[] movieExtends = mp4Box("mvex", new byte[0]);
        return concat(fileType, mp4Box("moov", concat(movieHeader, movieExtends)));
    }

    private byte[] mp4(final double durationSeconds, final int version) {
        final int timeScale = 1_000;
        final long duration = Math.round(durationSeconds * timeScale);
        final byte[] fileType = mp4Box(
                "ftyp",
                concat(ascii("M4A "), new byte[4], ascii("M4A "), ascii("isom"))
        );
        final byte[] movieHeader = mp4Box("mvhd", mp4MovieHeader(version, timeScale, duration));
        return concat(fileType, mp4Box("moov", movieHeader));
    }

    private byte[] mp4MovieHeader(
            final int version,
            final int timeScale,
            final long duration
    ) {
        if (version == 0) {
            final ByteBuffer buffer = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte) version);
            buffer.put(new byte[3]);
            buffer.putInt(0);
            buffer.putInt(0);
            buffer.putInt(timeScale);
            buffer.putInt((int) duration);
            return buffer.array();
        }
        final ByteBuffer buffer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) version);
        buffer.put(new byte[3]);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putInt(timeScale);
        buffer.putLong(duration);
        return buffer.array();
    }

    private byte[] mp4Box(final String type, final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.allocate(8 + data.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(8 + data.length);
        buffer.put(ascii(type));
        buffer.put(data);
        return buffer.array();
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

    @Test
    @DisplayName("mvhd 뒤에 파일 범위를 벗어난 자식 box가 있으면 거부한다")
    void mvhd_뒤에_손상된_자식_box가_있으면_거부한다() {
        // given — 첫 mvhd 에서 멈추면 뒤쪽 손상을 보지 못하고 통과한다
        final byte[] movieHeader = mp4Box("mvhd", mp4MovieHeader(0, 1_000, 5_000));
        final ByteBuffer brokenTrack = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        brokenTrack.putInt(1_024);
        brokenTrack.put(ascii("trak"));
        final byte[] fileType = mp4Box(
                "ftyp",
                concat(ascii("M4A "), new byte[4], ascii("M4A "), ascii("isom"))
        );
        final MockMultipartFile audio = audio(
                "voice.mp4",
                "audio/mp4",
                concat(fileType, mp4Box("moov", concat(movieHeader, brokenTrack.array())))
        );

        // when & then
        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_INVALID);
    }

    @Test
    @DisplayName("mvhd가 두 개인 MP4 파일은 거부한다")
    void mvhd가_두_개인_MP4_파일은_거부한다() {
        // given — 어느 쪽 재생 시간이 맞는지 알 수 없다
        final byte[] shortHeader = mp4Box("mvhd", mp4MovieHeader(0, 1_000, 5_000));
        final byte[] longHeader = mp4Box("mvhd", mp4MovieHeader(0, 1_000, 60_000));
        final byte[] fileType = mp4Box(
                "ftyp",
                concat(ascii("M4A "), new byte[4], ascii("M4A "), ascii("isom"))
        );
        final MockMultipartFile audio = audio(
                "voice.mp4",
                "audio/mp4",
                concat(fileType, mp4Box("moov", concat(shortHeader, longHeader)))
        );

        // when & then
        assertError(audio, "audio/mp4", ErrorCode.AUDIO_DURATION_INVALID);
    }

    @Test
    @DisplayName("mvhd 뒤에 정상 trak이 있는 MP4 파일은 허용한다")
    void mvhd_뒤에_정상_trak이_있는_MP4_파일은_허용한다() {
        // given — 실제 iPhone 녹음은 moov 안에 trak, udta 를 함께 담는다
        final byte[] movieHeader = mp4Box("mvhd", mp4MovieHeader(0, 1_000, 5_000));
        final byte[] track = mp4Box("trak", new byte[32]);
        final byte[] userData = mp4Box("udta", new byte[16]);
        final byte[] fileType = mp4Box(
                "ftyp",
                concat(ascii("M4A "), new byte[4], ascii("M4A "), ascii("isom"))
        );
        final MockMultipartFile audio = audio(
                "voice.mp4",
                "audio/mp4",
                concat(fileType, mp4Box("moov", concat(movieHeader, track, userData)))
        );

        // when & then
        assertThatCode(() -> validator.validate(audio, "audio/mp4"))
                .doesNotThrowAnyException();
    }
}
