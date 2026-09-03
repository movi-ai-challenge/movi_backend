package com.movi_backend.domain.voice.application;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class AudioDurationValidator {

    private static final double MAXIMUM_DURATION_SECONDS = 15.0;

    /** 실패 원인을 가릴 때 남길 컨테이너 헤더 길이. box 구조를 알아볼 만큼만 본다. */
    private static final int HEX_HEAD_LENGTH = 64;
    private static final long EBML_ID = 0x1A45DFA3L;
    private static final long SEGMENT_ID = 0x18538067L;
    private static final long INFO_ID = 0x1549A966L;
    private static final long DOC_TYPE_ID = 0x4282L;
    private static final long TIMECODE_SCALE_ID = 0x2AD7B1L;
    private static final long DURATION_ID = 0x4489L;
    private static final long DEFAULT_TIMECODE_SCALE_NANOS = 1_000_000L;
    private static final String MP4_FILE_TYPE_BOX = "ftyp";
    private static final String MP4_MOVIE_BOX = "moov";
    private static final String MP4_MOVIE_HEADER_BOX = "mvhd";
    private static final String MP4_MOVIE_EXTENDS_BOX = "mvex";

    /**
     * 컨테이너에 재생 시간이 적혀 있지 않다는 표시.
     *
     * <p>브라우저의 MediaRecorder 는 녹음을 <b>말하는 도중에</b> 만든다. 그 시점에는
     * 전체 길이를 알 수 없어 헤더에 적지 못하고, 다 만든 뒤에도 되돌아가 채우지 않는
     * 경우가 있다. 손상된 파일이 아니라 규격대로 만들어진 파일이다.
     *
     * <ul>
     *   <li>WebM: {@code Segment} 크기가 unknown 으로 열려 있고 {@code Duration} 이 없다</li>
     *   <li>MP4: {@code mvex} 가 있는 조각난 형식이라 {@code mvhd} 재생 시간이 0이다</li>
     * </ul>
     */
    private static final double DURATION_NOT_WRITTEN = -1.0;
    private static final long MP4_EXTENDED_SIZE = 1L;
    private static final long MP4_TO_END_SIZE = 0L;

    public void validate(final MultipartFile audio, final String contentType) {
        final double durationSeconds;
        byte[] bytes = null;
        try {
            bytes = audio.getBytes();
            durationSeconds = readDurationSeconds(bytes, contentType);
        } catch (IOException | IllegalArgumentException exception) {
            logUnreadableAudio(audio, contentType, bytes, exception);
            throw new BusinessException(ErrorCode.AUDIO_DURATION_INVALID);
        }

        /*
         * 길이가 적혀 있지 않은 녹음은 재지 않고 통과시킨다. 여기서 막으면 브라우저에서
         * 녹음한 음성이 통째로 거부되는데, 화면을 보지 않는 사용자에게 그건 앱을 못
         * 쓴다는 뜻이다. 길이 제한은 STT 비용과 응답 지연을 막으려는 것이지 보안 통제가
         * 아니고, 파일 크기 상한(5MB)과 프론트의 15초 자동 정지가 함께 걸려 있다.
         */
        if (durationSeconds == DURATION_NOT_WRITTEN) {
            return;
        }
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new BusinessException(ErrorCode.AUDIO_DURATION_INVALID);
        }
        if (durationSeconds > MAXIMUM_DURATION_SECONDS) {
            throw new BusinessException(ErrorCode.AUDIO_DURATION_EXCEEDED);
        }
    }

    /**
     * 왜 읽지 못했는지 남긴다.
     *
     * <p>이 검증이 실패하면 사용자에게는 "다시 녹음해 주세요"만 나가고 원인은 사라진다.
     * 실제로 iPhone 에서 확인 발화가 막혔을 때 로그에 남은 것이 에러 코드뿐이라 어느
     * 단계에서 걸렸는지 알 수 없었다.
     *
     * <p>앞부분 바이트는 컨테이너 헤더이지 음성 내용이 아니다. 어떤 도구가 만든 파일인지,
     * 어떤 box 가 어떤 순서로 들어 있는지를 보려면 이것이 있어야 한다.
     */
    private void logUnreadableAudio(
            final MultipartFile audio,
            final String contentType,
            final byte[] bytes,
            final Exception exception
    ) {
        log.warn(
                "음성 파일 재생 시간을 읽지 못했습니다: contentType={}, size={}, 원인={}, head={}",
                contentType,
                audio.getSize(),
                exception.getMessage(),
                toHexHead(bytes)
        );
    }

    private String toHexHead(final byte[] bytes) {
        if (bytes == null) {
            return "(읽지 못함)";
        }
        final int length = Math.min(bytes.length, HEX_HEAD_LENGTH);
        final StringBuilder hex = new StringBuilder(length * 3);
        for (int index = 0; index < length; index++) {
            hex.append(String.format("%02x ", bytes[index]));
        }
        return hex.toString().trim();
    }

    private double readDurationSeconds(final byte[] bytes, final String contentType) {
        if (contentType.equals("audio/webm")) {
            return readWebmDurationSeconds(bytes);
        }
        if (contentType.equals("audio/mp4") || contentType.equals("audio/x-m4a")) {
            return readMp4DurationSeconds(bytes);
        }
        return readWavDurationSeconds(bytes);
    }

    private double readMp4DurationSeconds(final byte[] bytes) {
        boolean fileTypeFound = false;
        Double durationSeconds = null;
        int offset = 0;
        while (offset < bytes.length) {
            final Mp4Box box = readMp4Box(bytes, offset, bytes.length);
            if (box.type().equals(MP4_FILE_TYPE_BOX)) {
                validateMp4FileType(box);
                fileTypeFound = true;
            } else if (box.type().equals(MP4_MOVIE_BOX)) {
                durationSeconds = readMp4MovieDurationSeconds(bytes, box);
            }
            offset = box.endOffset();
        }

        if (!fileTypeFound || durationSeconds == null) {
            throw new IllegalArgumentException("MP4 재생 시간 정보 누락");
        }
        return durationSeconds;
    }

    private void validateMp4FileType(final Mp4Box box) {
        if (box.dataSize() < 8) {
            throw new IllegalArgumentException("MP4 ftyp box가 너무 짧음");
        }
    }

    /**
     * {@code moov}의 자식 box를 끝까지 순회하며 재생 시간을 읽는다.
     *
     * <p>첫 {@code mvhd}에서 바로 반환하면 그 뒤에 크기가 파일 범위를 벗어나는 손상된
     * {@code trak}이 있어도 검증을 통과한다. 최상위 box 루프는 이미 끝까지 도는데
     * 여기서만 멈추면 같은 손상이 위치에 따라 통과와 거부로 갈린다.
     *
     * <p>{@code mvhd}가 둘이면 어느 쪽 재생 시간이 맞는지 알 수 없으므로 거부한다.
     */
    private double readMp4MovieDurationSeconds(final byte[] bytes, final Mp4Box movieBox) {
        Double durationSeconds = null;
        boolean movieHeaderFound = false;
        boolean fragmented = false;
        int offset = movieBox.dataOffset();
        while (offset < movieBox.endOffset()) {
            final Mp4Box child = readMp4Box(bytes, offset, movieBox.endOffset());
            if (child.type().equals(MP4_MOVIE_HEADER_BOX)) {
                if (movieHeaderFound) {
                    throw new IllegalArgumentException("MP4 mvhd box가 중복됨");
                }
                movieHeaderFound = true;
                durationSeconds = readWrittenMovieDurationSeconds(bytes, child);
            } else if (child.type().equals(MP4_MOVIE_EXTENDS_BOX)) {
                fragmented = true;
            }
            offset = child.endOffset();
        }

        if (!movieHeaderFound) {
            throw new IllegalArgumentException("MP4 mvhd box 누락");
        }
        if (durationSeconds != null) {
            return durationSeconds;
        }
        /*
         * mvhd 에 길이가 없다. mvex 가 함께 있으면 조각난 MP4 라 정상이고, 없으면
         * 실제로 읽을 수 없는 파일이라 거부한다.
         */
        if (fragmented) {
            return DURATION_NOT_WRITTEN;
        }
        throw new IllegalArgumentException("MP4 재생 시간 값이 유효하지 않음");
    }

    /** {@code mvhd} 에 적힌 재생 시간. 적혀 있지 않으면 {@code null}. */
    private Double readWrittenMovieDurationSeconds(final byte[] bytes, final Mp4Box movieHeaderBox) {
        try {
            return readMp4MovieHeaderDurationSeconds(bytes, movieHeaderBox);
        } catch (final MissingMovieDurationException exception) {
            return null;
        }
    }

    private double readMp4MovieHeaderDurationSeconds(
            final byte[] bytes,
            final Mp4Box movieHeaderBox
    ) {
        if (movieHeaderBox.dataSize() < 4) {
            throw new IllegalArgumentException("MP4 mvhd box가 너무 짧음");
        }
        final int version = Byte.toUnsignedInt(bytes[movieHeaderBox.dataOffset()]);
        if (version == 0) {
            return readMp4VersionZeroDurationSeconds(bytes, movieHeaderBox);
        }
        if (version == 1) {
            return readMp4VersionOneDurationSeconds(bytes, movieHeaderBox);
        }
        throw new IllegalArgumentException("지원하지 않는 MP4 mvhd 버전");
    }

    private double readMp4VersionZeroDurationSeconds(
            final byte[] bytes,
            final Mp4Box movieHeaderBox
    ) {
        final int requiredSize = 20;
        if (movieHeaderBox.dataSize() < requiredSize) {
            throw new IllegalArgumentException("MP4 mvhd 버전 0 데이터가 너무 짧음");
        }
        final int dataOffset = movieHeaderBox.dataOffset();
        final long timeScale = readUnsignedBigEndian(bytes, dataOffset + 12, 4);
        final double duration = readUnsignedBigEndianAsDouble(bytes, dataOffset + 16, 4);
        return calculateMp4DurationSeconds(timeScale, duration);
    }

    private double readMp4VersionOneDurationSeconds(
            final byte[] bytes,
            final Mp4Box movieHeaderBox
    ) {
        final int requiredSize = 32;
        if (movieHeaderBox.dataSize() < requiredSize) {
            throw new IllegalArgumentException("MP4 mvhd 버전 1 데이터가 너무 짧음");
        }
        final int dataOffset = movieHeaderBox.dataOffset();
        final long timeScale = readUnsignedBigEndian(bytes, dataOffset + 20, 4);
        final double duration = readUnsignedBigEndianAsDouble(bytes, dataOffset + 24, 8);
        return calculateMp4DurationSeconds(timeScale, duration);
    }

    private double calculateMp4DurationSeconds(final long timeScale, final double duration) {
        if (timeScale <= 0 || !Double.isFinite(duration)) {
            throw new IllegalArgumentException("MP4 재생 시간 값이 유효하지 않음");
        }
        /*
         * 0 은 "길이가 0인 파일"이 아니라 "여기에 길이를 적지 않았다"는 뜻이다. 조각난
         * MP4 에서는 정상이므로 형식이 깨진 경우와 구분해서 올린다.
         */
        if (duration <= 0) {
            throw new MissingMovieDurationException();
        }
        return duration / timeScale;
    }

    /** {@code mvhd} 가 재생 시간을 적지 않은 경우. 조각난 MP4 에서는 정상이다. */
    private static final class MissingMovieDurationException extends IllegalArgumentException {

        private MissingMovieDurationException() {
            super("MP4 mvhd에 재생 시간이 없음");
        }
    }

    private Mp4Box readMp4Box(final byte[] bytes, final int offset, final int limit) {
        final int standardHeaderSize = 8;
        if (offset < 0 || limit > bytes.length || offset + standardHeaderSize > limit) {
            throw new IllegalArgumentException("MP4 box 헤더 누락");
        }

        final long declaredSize = readUnsignedBigEndian(bytes, offset, 4);
        final String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
        int headerSize = standardHeaderSize;
        long boxSize = declaredSize;
        if (declaredSize == MP4_EXTENDED_SIZE) {
            headerSize = 16;
            boxSize = readSupportedMp4ExtendedSize(bytes, offset, limit);
        } else if (declaredSize == MP4_TO_END_SIZE) {
            boxSize = limit - (long) offset;
        }

        if (boxSize < headerSize || boxSize > limit - (long) offset) {
            throw new IllegalArgumentException("MP4 box 크기가 유효하지 않음");
        }
        final long endOffset = offset + boxSize;
        if (endOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("MP4 box 범위가 너무 큼");
        }
        return new Mp4Box(type, offset + headerSize, (int) boxSize - headerSize);
    }

    private long readSupportedMp4ExtendedSize(
            final byte[] bytes,
            final int offset,
            final int limit
    ) {
        final int extendedHeaderSize = 16;
        if (offset + extendedHeaderSize > limit) {
            throw new IllegalArgumentException("MP4 확장 box 헤더 누락");
        }
        for (int index = offset + 8; index < offset + 12; index++) {
            if (bytes[index] != 0) {
                throw new IllegalArgumentException("MP4 확장 box 범위가 너무 큼");
            }
        }
        return readUnsignedBigEndian(bytes, offset + 12, 4);
    }

    private double readUnsignedBigEndianAsDouble(
            final byte[] bytes,
            final int offset,
            final int length
    ) {
        if (offset < 0 || length < 1 || length > 8 || offset + length > bytes.length) {
            throw new IllegalArgumentException("정수 범위가 유효하지 않음");
        }
        double value = 0;
        for (int index = 0; index < length; index++) {
            value = value * 256 + Byte.toUnsignedInt(bytes[offset + index]);
        }
        return value;
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
                return readInfoDurationSeconds(bytes, element, segment.unknownSize());
            }
            offset = element.endOffset();
        }
        throw new IllegalArgumentException("WebM Info 누락");
    }

    /**
     * {@code Info} 에서 재생 시간을 읽는다.
     *
     * @param openSegment {@code Segment} 크기가 unknown 인지 여부. 녹음이 끝나기 전에
     *                    쓰인 컨테이너라는 뜻이라, 이 경우 {@code Duration} 이 없는 것은
     *                    정상이다
     */
    private double readInfoDurationSeconds(
            final byte[] bytes,
            final EbmlElement info,
            final boolean openSegment
    ) {
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

        if (timecodeScale <= 0) {
            throw new IllegalArgumentException("WebM TimecodeScale 값이 유효하지 않음");
        }
        /*
         * Segment 를 크기 없이 열어 둔 채로 쓴 파일이다. 브라우저가 말하는 도중에 만드는
         * 형태이고, 그때는 전체 길이를 알 수 없어 Duration 을 적지 못한다. 크기가 적힌
         * Segment 인데 Duration 이 없으면 그때는 실제로 읽을 수 없는 파일이라 거부한다.
         */
        if (duration == null || duration <= 0) {
            if (openSegment) {
                return DURATION_NOT_WRITTEN;
            }
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
        return new EbmlElement(id.value(), dataOffset, (int) dataSize, size.unknown());
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

    private record EbmlElement(long id, int dataOffset, int dataSize, boolean unknownSize) {

        private int endOffset() {
            return dataOffset + dataSize;
        }
    }

    private record Mp4Box(String type, int dataOffset, int dataSize) {

        private int endOffset() {
            return dataOffset + dataSize;
        }
    }
}
