package com.movi_backend.domain.notification.infrastructure.solapi;

/** 솔라피 인증 헤더 구성 요소. {@code signature}는 {@code date+salt}를 API Secret으로 서명한 값이다. */
record SolapiSignature(String date, String salt, String signature) {

    private static final String AUTHORIZATION_FORMAT =
            "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s";

    String toAuthorizationHeader(final String apiKey) {
        return AUTHORIZATION_FORMAT.formatted(apiKey, date, salt, signature);
    }
}
