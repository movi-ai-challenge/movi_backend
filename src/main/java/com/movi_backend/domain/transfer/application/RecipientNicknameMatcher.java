package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.entity.TransferRecipient;
import java.util.List;
import java.util.Optional;

/**
 * STT가 한글 이름 한 음소를 잘못 들은 경우 등록 수취인 별칭으로 안전하게 보정한다.
 *
 * <p>편집 거리가 1 이하인 후보가 정확히 하나일 때만 선택한다. 같은 거리의 후보가 둘이면
 * 어느 사람인지 추측하지 않는다. 송금 대상 선택에서는 잘못 고르는 것보다 다시 묻는 편이
 * 안전하다.
 */
final class RecipientNicknameMatcher {

    private static final int MAXIMUM_DISTANCE = 1;
    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;
    private static final int MEDIAL_COUNT = 21;
    private static final int FINAL_COUNT = 28;

    private RecipientNicknameMatcher() {
    }

    static Optional<TransferRecipient> findUniqueClosest(
            final String spokenNickname,
            final List<TransferRecipient> recipients
    ) {
        final String spoken = decompose(normalize(spokenNickname));
        TransferRecipient closest = null;
        int closestDistance = MAXIMUM_DISTANCE + 1;
        boolean ambiguous = false;

        for (final TransferRecipient recipient : recipients) {
            final String candidate = decompose(normalize(recipient.getNickname()));
            final int distance = levenshteinDistance(spoken, candidate);
            if (distance > MAXIMUM_DISTANCE) {
                continue;
            }
            if (distance < closestDistance) {
                closest = recipient;
                closestDistance = distance;
                ambiguous = false;
            } else if (distance == closestDistance) {
                ambiguous = true;
            }
        }

        return ambiguous ? Optional.empty() : Optional.ofNullable(closest);
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 한글 음절을 초성·중성·종성 코드로 풀어 발음 차이를 한 번의 편집으로 센다. */
    private static String decompose(final String value) {
        final StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint < HANGUL_BASE || codePoint > HANGUL_END) {
                result.appendCodePoint(codePoint);
                return;
            }
            final int syllable = codePoint - HANGUL_BASE;
            result.appendCodePoint(0x1100 + syllable / (MEDIAL_COUNT * FINAL_COUNT));
            result.appendCodePoint(0x1161 + (syllable % (MEDIAL_COUNT * FINAL_COUNT)) / FINAL_COUNT);
            final int finalConsonant = syllable % FINAL_COUNT;
            if (finalConsonant > 0) {
                result.appendCodePoint(0x11A7 + finalConsonant);
            }
        });
        return result.toString();
    }

    private static int levenshteinDistance(final String left, final String right) {
        final int[] leftPoints = left.codePoints().toArray();
        final int[] rightPoints = right.codePoints().toArray();
        int[] previous = new int[rightPoints.length + 1];
        int[] current = new int[rightPoints.length + 1];
        for (int column = 0; column <= rightPoints.length; column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= leftPoints.length; row++) {
            current[0] = row;
            for (int column = 1; column <= rightPoints.length; column++) {
                final int substitution = leftPoints[row - 1] == rightPoints[column - 1] ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitution
                );
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[rightPoints.length];
    }
}
