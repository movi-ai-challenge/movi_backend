package com.movi_backend.global.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveTextMasker {

    private static final Pattern SENSITIVE_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d[\\s().-]*){9,15}\\d(?!\\d)"
    );
    private static final int VISIBLE_DIGITS = 4;

    private SensitiveTextMasker() {
    }

    public static boolean containsSensitiveNumber(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return SENSITIVE_NUMBER.matcher(text).find();
    }

    public static String mask(final String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        final Matcher matcher = SENSITIVE_NUMBER.matcher(text);
        final StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            final String digits = matcher.group().replaceAll("\\D", "");
            final String lastDigits = digits.substring(digits.length() - VISIBLE_DIGITS);
            matcher.appendReplacement(masked, Matcher.quoteReplacement("***" + lastDigits));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }
}
