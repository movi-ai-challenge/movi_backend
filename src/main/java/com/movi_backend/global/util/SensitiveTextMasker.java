package com.movi_backend.global.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveTextMasker {

    private static final Pattern SENSITIVE_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d[\\s().-]*){9,15}\\d(?!\\d)"
    );
    private static final int VISIBLE_DIGITS = 4;
    private static final String MASKED_ONLY = "***";

    private SensitiveTextMasker() {
    }

    public static boolean containsSensitiveNumber(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return SENSITIVE_NUMBER.matcher(text).find();
    }

    /**
     * 계좌번호를 뒤 네 자리만 남기고 가린다.
     *
     * <p>{@link #mask(String)}는 자유 문장 안의 긴 숫자를 찾아 가리는 용도라 자릿수가 짧으면
     * 아무것도 가리지 않는다. 계좌번호는 길이와 상관없이 반드시 가려야 하므로 따로 둔다.
     *
     * <p>가릴 값이 없거나 자릿수가 모자라면 {@code "***"}다. 빈 값에만 {@code null}을 주면
     * 응답의 {@code maskedAccountNumber}가 경우에 따라 두 가지 모양이 된다.
     */
    public static String maskAccountNumber(final String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return MASKED_ONLY;
        }
        final String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() <= VISIBLE_DIGITS) {
            return MASKED_ONLY;
        }
        return "***" + digits.substring(digits.length() - VISIBLE_DIGITS);
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
