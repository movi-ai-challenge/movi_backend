package com.movi_backend.domain.transfer.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 발화에서 계좌번호를 뽑는다.
 *
 * <p>STT는 같은 숫자를 두 가지로 적어 온다. "삼오이이"처럼 한글로 적을 때도 있고
 * "3522"처럼 아라비아 숫자로 적을 때도 있으며, 한 문장 안에 섞이기도 한다
 * ("삼오이이 2315749"). 둘 다 같은 자리로 읽어야 한다.
 *
 * <p><b>자릿수를 지어내지 않는다.</b> "삼천오백"처럼 자릿수를 품은 수사는 계좌번호로
 * 읽지 않는다. 계좌번호는 자리마다 하나씩 읽는 것이 정상이고, 수사를 계좌번호로 풀면
 * 사용자가 말하지 않은 숫자가 생긴다 — 그대로 두면 모르는 계좌로 돈이 간다.
 *
 * <p>이 판단은 <b>숫자 덩어리마다</b> 한다. 문장 전체에 수사가 하나라도 있으면 읽지 않는
 * 방식은 쓸 수 없다 — "만원"의 "만" 때문에 금액을 한국어로 말하는 거의 모든 발화에서
 * 계좌번호를 잃는다.
 */
@Component
public class SpokenAccountNumberParser {

    /** 오픈뱅킹 계좌번호 길이 범위. 이 밖이면 잘못 들은 것으로 본다. */
    private static final int MINIMUM_LENGTH = 9;
    private static final int MAXIMUM_LENGTH = 16;

    private static final Map<Character, Character> DIGIT_BY_SYLLABLE = Map.ofEntries(
            Map.entry('영', '0'), Map.entry('공', '0'), Map.entry('제', '0'),
            Map.entry('일', '1'), Map.entry('하', '1'),
            Map.entry('이', '2'),
            Map.entry('삼', '3'),
            Map.entry('사', '4'),
            Map.entry('오', '5'),
            Map.entry('육', '6'), Map.entry('륙', '6'),
            Map.entry('칠', '7'),
            Map.entry('팔', '8'),
            Map.entry('구', '9')
    );

    /**
     * 자릿수를 품은 수사. 이 글자가 있으면 계좌번호로 읽지 않는다.
     *
     * <p>"삼천오백이십"을 자리마다 읽으면 3-5-2가 되는데 사용자가 말한 것은 3520이다.
     * 어느 쪽으로 풀어도 틀릴 수 있으므로 아예 받지 않고 다시 묻는다.
     */
    private static final String PLACE_VALUE_SYLLABLES = "십백천만억";

    public Optional<String> parse(final String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }

        final List<String> candidates = new ArrayList<>();
        final StringBuilder run = new StringBuilder();
        boolean poisoned = false;

        for (final char character : transcript.toCharArray()) {
            if (isPlaceValue(character)) {
                // 수사 안에 섞인 숫자다. 이 덩어리는 계좌번호로 읽지 않는다.
                poisoned = true;
                continue;
            }
            final Character digit = digitOf(character);
            if (digit != null) {
                run.append(digit);
                continue;
            }
            if (isSeparator(character)) {
                // 하이픈·공백은 계좌번호 안에 흔히 섞인다. 덩어리를 끊지 않는다.
                continue;
            }
            closeRun(run, poisoned, candidates);
            poisoned = false;
        }
        closeRun(run, poisoned, candidates);

        if (candidates.size() != 1) {
            // 후보가 없거나 둘 이상이면 어느 것이 계좌번호인지 단정할 수 없다.
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private void closeRun(
            final StringBuilder run,
            final boolean poisoned,
            final List<String> candidates
    ) {
        final String digits = run.toString();
        run.setLength(0);
        if (poisoned) {
            return;
        }
        if (digits.length() < MINIMUM_LENGTH || digits.length() > MAXIMUM_LENGTH) {
            return;
        }
        candidates.add(digits);
    }

    private Character digitOf(final char character) {
        if (Character.isDigit(character)) {
            return character;
        }
        return DIGIT_BY_SYLLABLE.get(character);
    }

    private boolean isSeparator(final char character) {
        return character == '-' || character == ' ' || character == '.'
                || character == '(' || character == ')';
    }

    private boolean isPlaceValue(final char character) {
        return PLACE_VALUE_SYLLABLES.indexOf(character) >= 0;
    }

    /**
     * 계좌번호를 자리마다 끊어 읽을 문자열로 바꾼다.
     *
     * <p>TTS는 {@code 3522315749}를 "삼십오억 이천이백삼십일만..."으로 읽는다. 사용자가
     * 자기 계좌인지 확인할 수 없으므로 한 자리씩 떼어 준다.
     */
    public String toSpokenDigits(final String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        final StringBuilder spoken = new StringBuilder();
        for (final char character : accountNumber.toCharArray()) {
            if (!Character.isDigit(character)) {
                continue;
            }
            if (spoken.length() > 0) {
                spoken.append(' ');
            }
            spoken.append(character);
        }
        return spoken.toString();
    }
}
