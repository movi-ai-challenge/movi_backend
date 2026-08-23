package com.movi_backend.domain.voice.application;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AudioDurationValidator {

    private static final double MAXIMUM_DURATION_SECONDS = 15.0;
    private static final long EBML_ID = 0x1A45DFA3L;
    private static final long SEGMENT_ID = 0x18538067L;
    private static final long INFO_ID = 0x1549A966L;
    private static final long DOC_TYPE_ID = 0x4282L;
    private static final long TIMECODE_SCALE_ID = 0x2AD7B1L;
    private static final long DURATION_ID = 0x4489L;
    private static final long DEFAULT_TIMECODE_SCALE_NANOS = 1_000_000L;

    public void validate(final MultipartFile audio, final String contentType) {
        final double durationSeconds;
        try {
            final byte[] bytes = audio.getBytes();
            durationSeconds = contentType.equals("audio/webm")
                    ? readWebmDurationSeconds(bytes)
                    : readWavDurationSeconds(bytes);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUDIO_DURATION_INVALID);
        }

        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new BusinessException(ErrorCode.AUDIO_DURATION_INVALID);
        }
        if (durationSeconds > MAXIMUM_DURATION_SECONDS) {
            throw new BusinessException(ErrorCode.AUDIO_DURATION_EXCEEDED);
        }
    }

    private double readWavDurationSeconds(final byte[] bytes) {
        if (bytes.length < 12
                || !hasAscii(bytes, 0, "RIFF")
                || !hasAscii(bytes, 8, "WAVE")) {
            throw new IllegalArgumentException("유효하지 않은 WAV 헤더");
        }

        long byteRate = -1;
        long dataSize = -1;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            final String chunkId = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            final long chunkSize = readUnsignedLittleEndian(bytes, offset + 4, 4);
            final long dataOffset = offset + 8L;
            final long chunkEnd = dataOffset + chunkSize;
            if (chunkEnd > bytes.length) {
                throw new IllegalArgumentException("WAV 청크 크기가 파일 범위를 벗어남");
            }

            if (chunkId.equals("fmt ")) {
                if (chunkSize < 16) {
                    throw new IllegalArgumentException("WAV fmt 청크가 너무 짧음");
                }
                byteRate = readUnsignedLittleEndian(bytes, (int) dataOffset + 8, 4);
            } else if (chunkId.equals("data")) {
                dataSize = chunkSize;
            }

            final long nextOffset = chunkEnd + (chunkSize & 1L);
            if (nextOffset > bytes.length || nextOffset > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("WAV 청크 오프셋이 유효하지 않음");
            }
            offset = (int) nextOffset;
        }

        if (byteRate <= 0 || dataSize <= 0) {
            throw new IllegalArgumentException("WAV 재생 시간 정보 누락");
        }
        return (double) dataSize / byteRate;
    }

    private double readWebmDurationSeconds(final byte[] bytes) {
        int offset = 0;
        final EbmlElement header = readElement(bytes, offset, bytes.length);
        if (header.id() != EBML_ID) {
            throw new IllegalArgumentException("EBML 헤더 누락");
        }
        validateWebmDocType(bytes, header);
        offset = header.endOffset();

        while (offset < bytes.length) {
            final EbmlElement element = readElement(bytes, offset, bytes.length);
            if (element.id() == SEGMENT_ID) {
                return readSegmentDurationSeconds(bytes, element);
            }
            offset = element.endOffset();
        }
        throw new IllegalArgumentException("WebM Segment 누락");
    }

    private void validateWebmDocType(final byte[] bytes, final EbmlElement header) {
        int offset = header.dataOffset();
        while (offset < header.endOffset()) {
            final EbmlElement element = readElement(bytes, offset, header.endOffset());
            if (element.id() == DOC_TYPE_ID) {
                final String docType = new String(
                        bytes,
                        element.dataOffset(),
                        element.dataSize(),
                        StandardCharsets.US_ASCII
                );
                if (!docType.equals("webm")) {
                    throw new IllegalArgumentException("WebM DocType 불일치");
                }
                return;
            }
            offset = element.endOffset();
        }
        throw new IllegalArgumentException("WebM DocType 누락");
    }

    private double readSegmentDurationSeconds(final byte[] bytes, final EbmlElement segment) {
        int offset = segment.dataOffset();
        while (offset < segment.endOffset()) {
            final EbmlElement element = readElement(bytes, offset, segment.endOffset());
            if (element.id() == INFO_ID) {
                return readInfoDurationSeconds(bytes, element);
            }
            offset = element.endOffset();
        }
        throw new IllegalArgumentException("WebM Info 누락");
    }

    private double readInfoDurationSeconds(final byte[] bytes, final EbmlElement info) {
        long timecodeScale = DEFAULT_TIMECODE_SCALE_NANOS;
        Double duration = null;
        int offset = info.dataOffset();
        while (offset < info.endOffset()) {
            final EbmlElement element = readElement(bytes, offset, info.endOffset());
            if (element.id() == TIMECODE_SCALE_ID) {
                timecodeScale = readUnsignedBigEndian(
                        bytes,
                        element.dataOffset(),
                        element.dataSize()
                );
            } else if (element.id() == DURATION_ID) {
                duration = readEbmlFloat(bytes, element.dataOffset(), element.dataSize());
            }
            offset = element.endOffset();
        }

        if (timecodeScale <= 0 || duration == null) {
            throw new IllegalArgumentException("WebM 재생 시간 정보 누락");
        }
        return duration * timecodeScale / 1_000_000_000.0;
    }

    private EbmlElement readElement(final byte[] bytes, final int offset, final int limit) {
        final VariableInteger id = readVariableInteger(bytes, offset, limit, true);
        final VariableInteger size = readVariableInteger(bytes, offset + id.length(), limit, false);
        final int dataOffset = offset + id.length() + size.length();
        final long available = limit - (long) dataOffset;
        final long dataSize = size.unknown() ? available : size.value();
        if (dataSize < 0 || dataSize > available || dataSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("EBML 요소 크기가 유효하지 않음");
        }
        return new EbmlElement(id.value(), dataOffset, (int) dataSize);
    }

    private VariableInteger readVariableInteger(
            final byte[] bytes,
            final int offset,
            final int limit,
            final boolean preserveMarker
    ) {
        if (offset >= limit) {
            throw new IllegalArgumentException("EBML 가변 정수 누락");
        }
        final int first = Byte.toUnsignedInt(bytes[offset]);
        int length = 1;
        int marker = 0x80;
        while (length <= 8 && (first & marker) == 0) {
            length++;
            marker >>>= 1;
        }
        if (length > 8 || offset + length > limit || (preserveMarker && length > 4)) {
            throw new IllegalArgumentException("EBML 가변 정수 길이가 유효하지 않음");
        }

        long value = preserveMarker ? first : first & (marker - 1);
        for (int index = 1; index < length; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        final long unknownValue = (1L << (7 * length)) - 1;
        return new VariableInteger(value, length, !preserveMarker && value == unknownValue);
    }

    private double readEbmlFloat(final byte[] bytes, final int offset, final int length) {
        if (length == 4) {
            return Float.intBitsToFloat((int) readUnsignedBigEndian(bytes, offset, length));
        }
        if (length == 8) {
            return Double.longBitsToDouble(readUnsignedBigEndian(bytes, offset, length));
        }
        throw new IllegalArgumentException("WebM Duration 형식이 유효하지 않음");
    }

    private long readUnsignedBigEndian(final byte[] bytes, final int offset, final int length) {
        if (length < 1 || length > 8 || offset + length > bytes.length) {
            throw new IllegalArgumentException("정수 범위가 유효하지 않음");
        }
        long value = 0;
        for (int index = 0; index < length; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        return value;
    }

    private long readUnsignedLittleEndian(final byte[] bytes, final int offset, final int length) {
        if (offset < 0 || length < 1 || length > 4 || offset + length > bytes.length) {
            throw new IllegalArgumentException("정수 범위가 유효하지 않음");
        }
        long value = 0;
        for (int index = 0; index < length; index++) {
            value |= (long) Byte.toUnsignedInt(bytes[offset + index]) << (8 * index);
        }
        return value;
    }

    private boolean hasAscii(final byte[] bytes, final int offset, final String expected) {
        if (offset + expected.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private record VariableInteger(long value, int length, boolean unknown) {
    }

    private record EbmlElement(long id, int dataOffset, int dataSize) {

        private int endOffset() {
            return dataOffset + dataSize;
        }
    }
}
