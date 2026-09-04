package com.movi_backend.domain.transfer.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 발화에서 금액을 뽑는다.
 *
 * <p><b>모델이 한국어 복합 금액을 안정적으로 읽지 못해서 둔다.</b> 운영에서 측정한 결과
 * "십만 이천원"은 4회 중 4회 120,000으로 왔다 — 사용자가 말한 102,000이 아니다.
 * "십이만 삼천원"은 맞게 읽으므로 모든 복합 금액이 아니라 특정 형태에서 무너진다.
 * 어느 형태가 무너질지 미리 알 수 없으니, 돈이 걸린 값은 우리가 직접 읽는다.
 *
 * <p>한국어 수사는 규칙이 닫혀 있어 결정적으로 풀 수 있다. 읽어낸 값이 있으면 그것을
 * 쓰고, 못 읽으면 모델 값을 그대로 둔다 — 대신하는 것이지 막는 것이 아니다.
 *
 * <p><b>"원"에 붙여서만 읽는다.</b> 발화에는 계좌번호처럼 금액이 아닌 숫자가 함께 온다
 * ("삼오이이삼일오칠사구로 만원 보내줘"). 금액은 거의 언제나 "원"으로 끝나므로, 그
 * 앞에 붙은 수사만 본다. "병원"·"지원"처럼 원으로 끝나는 낱말은 앞에 숫자가 없어
 * 자연히 걸러진다.
 */
@Component
public class SpokenAmountParser {

    /**
     * 이 위로는 읽지 않는다.
     *
     * <p>계좌번호가 배수와 붙으면 (" 3522315749 만원") 조 단위 금액이 나온다. 이체 한도는
     * 훨씬 아래라 어차피 거절되지만, 그 전에 확인 문구가 사용자에게 엉뚱한 금액을 읽어
     * 준다. 말이 안 되는 값은 읽지 못한 것으로 본다.
     */
    private static final long MAXIMUM_AMOUNT = 100_000_000L;

    private static final Map<Character, Integer> DIGIT_BY_SYLLABLE = Map.ofEntries(
            Map.entry('영', 0), Map.entry('공', 0),
            Map.entry('일', 1),
            Map.entry('이', 2),
            Map.entry('삼', 3),
            Map.entry('사', 4),
            Map.entry('오', 5),
            Map.entry('육', 6), Map.entry('륙', 6),
            Map.entry('칠', 7),
            Map.entry('팔', 8),
            Map.entry('구', 9)
    );

    /** 만 아래의 자릿수. 한 덩어리 안에서 더해진다. */
    private static final Map<Character, Integer> SMALL_PLACE_BY_SYLLABLE = Map.of(
            '십', 10,
            '백', 100,
            '천', 1_000
    );

    /** 만 이상의 자릿수. 앞 덩어리 전체에 곱해진다. */
    private static final Map<Character, Long> BIG_PLACE_BY_SYLLABLE = Map.of(
            '만', 10_000L,
            '억', 100_000_000L
    );

    private static final char WON = '원';

    /**
     * 발화에서 금액을 읽는다. 확신할 수 없으면 비운다.
     *
     * <p>서로 다른 금액이 둘 이상 나오면 읽지 않는다 — 어느 쪽이 보낼 금액인지 정할
     * 근거가 없다. {@code SpokenAccountNumberParser}가 계좌번호 후보를 다룰 때와 같다.
     */
    public Optional<Long> parse(final String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }

        final List<Long> candidates = new ArrayList<>();
        for (int index = 0; index < transcript.length(); index++) {
            if (transcript.charAt(index) != WON) {
                continue;
            }
            readAmountEndingAt(transcript, index).ifPresent(amount -> {
                if (!candidates.contains(amount)) {
                    candidates.add(amount);
                }
            });
        }

        if (candidates.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    /** {@code wonIndex} 바로 앞에 붙은 수사를 뒤에서 앞으로 모아 푼다. */
    private Optional<Long> readAmountEndingAt(final String transcript, final int wonIndex) {
        int start = wonIndex;
        boolean sawNumeral = false;
        while (start > 0 && isNumeralPart(transcript.charAt(start - 1))) {
            if (!isSkippable(transcript.charAt(start - 1))) {
                sawNumeral = true;
            }
            start--;
        }
        if (!sawNumeral) {
            // "병원"·"지원"처럼 원으로 끝나는 낱말이다.
            return Optional.empty();
        }
        return evaluate(transcript.substring(start, wonIndex));
    }

    /**
     * 한국어 수사를 값으로 푼다.
     *
     * <p>만·억은 앞에 쌓인 덩어리 전체에 곱하고, 십·백·천은 그 덩어리 안에서 더한다.
     * "십만 이천"이면 십(10) → 만에서 100,000이 되고, 이천(2,000)이 뒤에 더해져
     * 102,000이 된다.
     */
    private Optional<Long> evaluate(final String numeral) {
        long total = 0L;
        long section = 0L;
        long current = 0L;
        boolean sawDigit = false;

        for (final char character : numeral.toCharArray()) {
            final Integer digit = digitOf(character);
            if (digit != null) {
                current = current * 10L + digit;
                sawDigit = true;
                continue;
            }
            final Integer smallPlace = SMALL_PLACE_BY_SYLLABLE.get(character);
            if (smallPlace != null) {
                // "십만"처럼 앞 숫자가 없으면 1이다.
                section += (current == 0L ? 1L : current) * smallPlace;
                current = 0L;
                sawDigit = true;
                continue;
            }
            final Long bigPlace = BIG_PLACE_BY_SYLLABLE.get(character);
            if (bigPlace != null) {
                final long chunk = section + current;
                total += (chunk == 0L ? 1L : chunk) * bigPlace;
                section = 0L;
                current = 0L;
                sawDigit = true;
                continue;
            }
            // 공백·쉼표다. 수사를 끊지 않는다 ("십만 이천원").
        }

        final long amount = total + section + current;
        if (!sawDigit || amount <= 0L || amount > MAXIMUM_AMOUNT) {
            return Optional.empty();
        }
        return Optional.of(amount);
    }

    private boolean isNumeralPart(final char character) {
        return digitOf(character) != null
                || SMALL_PLACE_BY_SYLLABLE.containsKey(character)
                || BIG_PLACE_BY_SYLLABLE.containsKey(character)
                || isSkippable(character);
    }

    /** 수사 안에 섞여도 값에 영향을 주지 않는 글자. */
    private boolean isSkippable(final char character) {
        return character == ' ' || character == ',';
    }

    private Integer digitOf(final char character) {
        if (Character.isDigit(character)) {
            return Character.getNumericValue(character);
        }
        return DIGIT_BY_SYLLABLE.get(character);
    }
}
