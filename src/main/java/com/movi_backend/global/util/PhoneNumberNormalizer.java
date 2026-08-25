package com.movi_backend.global.util;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.regex.Pattern;

/**
 * 전화번호 정규화·마스킹.
 *
 * <p>같은 번호가 {@code 010-1234-5678}, {@code 010 1234 5678}, {@code +82 10 1234 5678}처럼
 * 여러 형태로 들어온다. 저장·해시 이전에 하나의 표기로 통일해야 중복 판별이 성립한다.
 *
 * <p><b>정규화 결과를 로그에 남기지 않는다.</b> 부득이하게 흔적을 남겨야 하면
 * {@link #mask(String)}를 쓴다.
 */
public final class PhoneNumberNormalizer {

    private static final Pattern NON_DIGIT_OR_PLUS = Pattern.compile("[^0-9+]");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^01[016789][0-9]{7,8}$");
    private static final String KOREA_COUNTRY_CODE_WITH_PLUS = "+82";
    private static final String KOREA_COUNTRY_CODE = "82";
    private static final String LOCAL_PREFIX = "0";
    private static final String MASK = "****";
    private static final int PREFIX_LENGTH = 3;
    private static final int SUFFIX_LENGTH = 4;

    private PhoneNumberNormalizer() {
    }

    /**
     * 구분자·국가번호를 제거해 {@code 01012345678} 형태로 통일한다.
     *
     * @throws BusinessException 휴대전화 번호 형식이 아닐 때
     */
    public static String normalize(final String rawPhoneNumber) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_NUMBER);
        }

        final String compact = NON_DIGIT_OR_PLUS.matcher(rawPhoneNumber).replaceAll("");
        final String normalized = stripCountryCode(compact);
        if (!MOBILE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_NUMBER);
        }
        return normalized;
    }

    /** 로그·감사 기록에 남길 수 있는 형태로 가린다. 예: {@code 010****5678} */
    public static String mask(final String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < PREFIX_LENGTH + SUFFIX_LENGTH) {
            return MASK;
        }
        final String prefix = phoneNumber.substring(0, PREFIX_LENGTH);
        final String suffix = phoneNumber.substring(phoneNumber.length() - SUFFIX_LENGTH);
        return prefix + MASK + suffix;
    }

    private static String stripCountryCode(final String compact) {
        if (compact.startsWith(KOREA_COUNTRY_CODE_WITH_PLUS)) {
            return LOCAL_PREFIX + compact.substring(KOREA_COUNTRY_CODE_WITH_PLUS.length());
        }
        if (compact.startsWith(KOREA_COUNTRY_CODE)) {
            return LOCAL_PREFIX + compact.substring(KOREA_COUNTRY_CODE.length());
        }
        return compact;
    }
}
