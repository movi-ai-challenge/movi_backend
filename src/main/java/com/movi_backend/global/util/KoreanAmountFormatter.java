package com.movi_backend.global.util;

/**
 * 금액을 TTS가 정확히 읽을 수 있는 한국어 표기로 바꾼다.
 *
 * <p>TTS 엔진이 {@code 53000원}을 "오만삼천원"으로 읽을지 "오삼공공공원"으로 읽을지 보장할 수
 * 없다. 화면을 보지 못하는 사용자에게 금액을 잘못 읽어 주는 것은 이체 사고로 직결되므로,
 * 백엔드가 읽을 문자열을 직접 만들어 내려보낸다.
 *
 * <pre>
 * KoreanAmountFormatter.toKoreanWon(50000)    // "오만 원"
 * KoreanAmountFormatter.toKoreanWon(53000)    // "오만 삼천 원"
 * KoreanAmountFormatter.toKoreanWon(10000)    // "만 원"
 * KoreanAmountFormatter.toKoreanWon(1234567)  // "백이십삼만 사천오백육십칠 원"
 * </pre>
 */
public final class KoreanAmountFormatter {

    private static final String[] DIGIT_NAMES = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL_UNIT_NAMES = {"", "십", "백", "천"};
    private static final long[] LARGE_UNIT_VALUES = {1_000_000_000_000L, 100_000_000L, 10_000L};
    private static final String[] LARGE_UNIT_NAMES = {"조", "억", "만"};
    private static final long DECIMAL_BASE = 10L;
    private static final String ZERO = "영";
    private static final String WON_SUFFIX = " 원";
    private static final String MAN_UNIT = "만";

    private KoreanAmountFormatter() {
    }

    /** 금액을 "오만 삼천 원" 형태로 바꾼다. */
    public static String toKoreanWon(final long amount) {
        return format(amount) + WON_SUFFIX;
    }

    /** 금액을 "오만 삼천" 형태로 바꾼다. 단위를 직접 붙이고 싶을 때 쓴다. */
    public static String format(final long amount) {
        if (amount == 0L) {
            return ZERO;
        }
        if (amount < 0L) {
            return "마이너스 " + format(-amount);
        }

        final StringBuilder spoken = new StringBuilder();
        long remainder = amount;
        for (int index = 0; index < LARGE_UNIT_VALUES.length; index++) {
            final long unitValue = LARGE_UNIT_VALUES[index];
            final long groupValue = remainder / unitValue;
            if (groupValue == 0L) {
                continue;
            }
            appendSpaceIfNeeded(spoken);
            spoken.append(formatGroup(groupValue, LARGE_UNIT_NAMES[index]));
            remainder = remainder % unitValue;
        }
        if (remainder > 0L) {
            appendSpaceIfNeeded(spoken);
            spoken.append(formatBelowTenThousand(remainder));
        }
        return spoken.toString();
    }

    private static String formatGroup(final long groupValue, final String unitName) {
        if (groupValue == 1L && MAN_UNIT.equals(unitName)) {
            return unitName;
        }
        return formatBelowTenThousand(groupValue) + unitName;
    }

    /** 만 미만의 수를 읽는다. 예: 5300 -> "오천삼백" */
    private static String formatBelowTenThousand(final long value) {
        final StringBuilder spoken = new StringBuilder();
        long place = 1000L;
        for (int position = 3; position >= 0; position--) {
            final int digit = (int) ((value / place) % DECIMAL_BASE);
            place = place / 10L;
            if (digit == 0) {
                continue;
            }
            if (digit == 1 && position > 0) {
                spoken.append(SMALL_UNIT_NAMES[position]);
                continue;
            }
            spoken.append(DIGIT_NAMES[digit]).append(SMALL_UNIT_NAMES[position]);
        }
        return spoken.toString();
    }

    private static void appendSpaceIfNeeded(final StringBuilder spoken) {
        if (spoken.length() == 0) {
            return;
        }
        spoken.append(' ');
    }
}
