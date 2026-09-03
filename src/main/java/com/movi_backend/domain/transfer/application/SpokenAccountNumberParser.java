package com.movi_backend.domain.transfer.application;

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
        if (containsPlaceValue(transcript)) {
            return Optional.empty();
        }

        final StringBuilder digits = new StringBuilder();
        for (final char character : transcript.toCharArray()) {
            if (Character.isDigit(character)) {
                digits.append(character);
                continue;
            }
            final Character mapped = DIGIT_BY_SYLLABLE.get(character);
            if (mapped != null) {
                digits.append(mapped);
            }
        }

        final String accountNumber = digits.toString();
        if (accountNumber.length() < MINIMUM_LENGTH
                || accountNumber.length() > MAXIMUM_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(accountNumber);
    }

    private boolean containsPlaceValue(final String transcript) {
        for (final char character : PLACE_VALUE_SYLLABLES.toCharArray()) {
            if (transcript.indexOf(character) >= 0) {
                return true;
            }
        }
        return false;
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
