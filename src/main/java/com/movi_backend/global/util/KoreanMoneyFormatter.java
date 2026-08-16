package com.movi_backend.global.util;

public final class KoreanMoneyFormatter {

    private static final String[] NUMBER_NAMES = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String[] LARGE_UNITS = {"", "만", "억", "조", "경"};

    private KoreanMoneyFormatter() {
    }

    public static String format(final long amount) {
        if (amount == 0L) {
            return "영원";
        }

        final StringBuilder result = new StringBuilder();
        long remaining = amount;
        int largeUnitIndex = 0;

        while (remaining > 0L) {
            final int group = (int) (remaining % 10_000L);
            if (group > 0) {
                final String groupText = formatGroup(group);
                result.insert(0, groupText + LARGE_UNITS[largeUnitIndex]);
                if (remaining >= 10_000L) {
                    result.insert(0, " ");
                }
            }
            remaining /= 10_000L;
            largeUnitIndex++;
        }

        return result.toString().trim() + "원";
    }

    private static String formatGroup(final int group) {
        final StringBuilder result = new StringBuilder();
        int remaining = appendDigitUnit(result, group, 1_000, "천");
        remaining = appendDigitUnit(result, remaining, 100, "백");
        if (remaining > 0) {
            result.append(remaining);
        }
        return result.toString();
    }

    private static int appendDigitUnit(
            final StringBuilder result,
            final int amount,
            final int divisor,
            final String unit
    ) {
        final int digit = amount / divisor;
        if (digit > 0) {
            result.append(NUMBER_NAMES[digit]);
            result.append(unit);
        }
        return amount % divisor;
    }
}
