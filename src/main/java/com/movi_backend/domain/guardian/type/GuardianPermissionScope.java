package com.movi_backend.domain.guardian.type;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보호자에게 허용한 권한 범위. {@code guardian_links.permission_scope}에 JSON으로 저장한다.
 *
 * <p>보호자에게 <b>이체 승인 권한은 없다.</b> MVP에서 사전 차단은 제외했고, 여기 있는 것은
 * 조회와 알림 수신 여부뿐이다.
 *
 * <p>이 컬럼에 들어가는 JSON은 {@link #toJson()}이 만든 고정 형태뿐이라 전용 파서로 읽는다.
 * 값이 없거나 형태가 다르면 비어 있는 결과를 돌려주고, <b>기본값을 여기서 정하지 않는다.</b>
 * 미설정 시의 정책은 기능명세가 확정하지 않은 사안이라 호출부의 설정에 맡긴다.
 *
 * @param viewBalance  잔액·거래내역 조회 허용 여부
 * @param receiveAlert 이상거래 알림 수신 여부
 */
public record GuardianPermissionScope(
        boolean viewBalance,
        boolean receiveAlert
) {

    public static final String VIEW_BALANCE_KEY = "view_balance";
    public static final String RECEIVE_ALERT_KEY = "receive_alert";

    private static final String JSON_FORMAT =
            "{\"" + VIEW_BALANCE_KEY + "\":%b,\"" + RECEIVE_ALERT_KEY + "\":%b}";
    private static final Pattern VIEW_BALANCE_PATTERN = booleanPatternOf(VIEW_BALANCE_KEY);
    private static final Pattern RECEIVE_ALERT_PATTERN = booleanPatternOf(RECEIVE_ALERT_KEY);

    /** 보호자 등록 시 부여하는 기본 권한 */
    public static GuardianPermissionScope defaultScope() {
        return new GuardianPermissionScope(true, true);
    }

    public static GuardianPermissionScope of(final boolean viewBalance, final boolean receiveAlert) {
        return new GuardianPermissionScope(viewBalance, receiveAlert);
    }

    /** 저장된 JSON에서 알림 수신 여부를 읽는다. 값이 없으면 비어 있는 결과를 돌려준다. */
    public static Optional<Boolean> readReceiveAlert(final String json) {
        return readBoolean(json, RECEIVE_ALERT_PATTERN);
    }

    /** 저장된 JSON에서 잔액 조회 허용 여부를 읽는다. */
    public static Optional<Boolean> readViewBalance(final String json) {
        return readBoolean(json, VIEW_BALANCE_PATTERN);
    }

    public String toJson() {
        return JSON_FORMAT.formatted(viewBalance, receiveAlert);
    }

    private static Optional<Boolean> readBoolean(final String json, final Pattern pattern) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        final Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Boolean.parseBoolean(matcher.group(1)));
    }

    private static Pattern booleanPatternOf(final String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
    }
}
