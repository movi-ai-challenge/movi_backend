package com.movi_backend.domain.fds.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FDS 가 짚은 근거를 사람이 알아들을 말로 바꾼다.
 *
 * <p>화면을 보지 않는 사용자에게 {@code NIGHT_TRANSACTION} 같은 코드는 아무 뜻이 없다.
 * 위험도가 {@code LOW} 로 나와도 "새벽 시간대이고 처음 보내는 계좌"라는 사실 자체는
 * 알려 줄 값어치가 있다. 돈을 보내기 전에 한 번 더 생각할 근거가 되기 때문이다.
 *
 * <p>모르는 코드는 조용히 버린다. AI 가 룰을 추가해도 사용자에게 영문 코드가 그대로
 * 읽히면 안 된다.
 */
public final class RiskReasonNarrator {

    private static final Map<String, String> PHRASES = new LinkedHashMap<>();

    static {
        // 금액 관련을 먼저 둔다. 여러 근거가 잡히면 사용자가 가장 크게 느끼는 것부터 읽힌다.
        PHRASES.put("HIGH_AMOUNT_RATIO", "평소보다 큰 금액이에요");
        PHRASES.put("EXTREME_AMOUNT_ZSCORE", "평소와 크게 다른 금액이에요");
        PHRASES.put("NEW_RECIPIENT", "처음 보내는 계좌예요");
        PHRASES.put("NIGHT_TRANSACTION", "늦은 밤이나 새벽 시간이에요");
        PHRASES.put("UNUSUAL_MEDIUM", "평소와 다른 방법으로 보내요");
        PHRASES.put("REPEATED_SAME_DAY", "오늘 이미 여러 번 보냈어요");
        PHRASES.put("REPEATED_TIME_BUCKET", "짧은 시간에 여러 번 보냈어요");
        PHRASES.put("CROSS_BANK", "다른 은행으로 보내요");
    }

    /** 한 번에 읽어 줄 근거 수. 다 읽으면 길어져 무엇이 중요한지 묻힌다. */
    private static final int MAX_REASONS = 3;

    private RiskReasonNarrator() {
    }

    public static List<String> describe(final List<String> reasonCodes) {
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            return List.of();
        }
        return PHRASES.entrySet().stream()
                .filter(entry -> reasonCodes.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .limit(MAX_REASONS)
                .toList();
    }

    /** 낭독용 한 문장. 근거가 없으면 빈 문자열이다. */
    public static String toSentence(final List<String> reasonCodes) {
        final List<String> phrases = describe(reasonCodes);
        if (phrases.isEmpty()) {
            return "";
        }
        return String.join(", ", phrases) + ".";
    }
}
