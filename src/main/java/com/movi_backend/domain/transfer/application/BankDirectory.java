package com.movi_backend.domain.transfer.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 발화에 담긴 은행 이름을 오픈뱅킹 은행 코드로 바꾼다.
 *
 * <p>사용자는 "농협", "농협은행", "엔에이치"처럼 제각기 부른다. STT도 정식 명칭으로
 * 적어 주지 않는다. 그래서 정식 명칭이 아니라 <b>발화에 나타날 법한 말</b>을 열쇠로 둔다.
 *
 * <p>먼저 등록된 순서대로 찾는다. "농협"이 "농협은행"보다 앞에 오면 "농협은행"을 말해도
 * "농협"에서 걸리는데, 어차피 같은 코드라 문제가 없다. 다른 은행끼리 겹치는 말은 넣지 않는다.
 */
@Component
public class BankDirectory {

    /** 발화에 쓰일 만한 말 -> 오픈뱅킹 은행 코드. 순서가 곧 우선순위다. */
    private static final Map<String, String> CODE_BY_SPOKEN_NAME = new LinkedHashMap<>();

    static {
        CODE_BY_SPOKEN_NAME.put("국민", "004");
        CODE_BY_SPOKEN_NAME.put("케이비", "004");
        CODE_BY_SPOKEN_NAME.put("KB", "004");
        CODE_BY_SPOKEN_NAME.put("신한", "088");
        CODE_BY_SPOKEN_NAME.put("우리", "020");
        CODE_BY_SPOKEN_NAME.put("하나", "081");
        CODE_BY_SPOKEN_NAME.put("농협", "011");
        CODE_BY_SPOKEN_NAME.put("엔에이치", "011");
        CODE_BY_SPOKEN_NAME.put("NH", "011");
        CODE_BY_SPOKEN_NAME.put("기업", "003");
        CODE_BY_SPOKEN_NAME.put("아이비케이", "003");
        CODE_BY_SPOKEN_NAME.put("카카오", "090");
        CODE_BY_SPOKEN_NAME.put("케이뱅크", "089");
        CODE_BY_SPOKEN_NAME.put("토스", "092");
        CODE_BY_SPOKEN_NAME.put("새마을", "045");
        CODE_BY_SPOKEN_NAME.put("우체국", "071");
        CODE_BY_SPOKEN_NAME.put("수협", "007");
        CODE_BY_SPOKEN_NAME.put("SC", "023");
        CODE_BY_SPOKEN_NAME.put("씨티", "027");
    }

    /** 목록에서는 빼는 코드. 사용자가 이름으로 구분할 수 없다. */
    private static final Set<String> HIDDEN_FROM_SELECTION = Set.of("012");

    private static final Map<String, String> DISPLAY_NAME_BY_CODE = Map.ofEntries(
            Map.entry("004", "국민은행"),
            Map.entry("088", "신한은행"),
            Map.entry("020", "우리은행"),
            Map.entry("081", "하나은행"),
            Map.entry("011", "농협은행"),
            Map.entry("003", "기업은행"),
            Map.entry("090", "카카오뱅크"),
            Map.entry("089", "케이뱅크"),
            Map.entry("092", "토스뱅크"),
            Map.entry("045", "새마을금고"),
            Map.entry("071", "우체국"),
            Map.entry("007", "수협은행"),
            Map.entry("023", "SC제일은행"),
            Map.entry("027", "씨티은행"),
            Map.entry("012", "농협중앙회")
    );

    public Optional<String> findCode(final String spokenBankName) {
        if (spokenBankName == null || spokenBankName.isBlank()) {
            return Optional.empty();
        }
        final String normalized = spokenBankName.replaceAll("\\s", "").toUpperCase();
        for (final Map.Entry<String, String> entry : CODE_BY_SPOKEN_NAME.entrySet()) {
            if (normalized.contains(entry.getKey().toUpperCase())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /** 확인 문구에 읽어 줄 이름. 모르는 코드면 코드를 그대로 읽는다. */
    public String displayNameOf(final String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return "";
        }
        return DISPLAY_NAME_BY_CODE.getOrDefault(bankCode, bankCode);
    }

    /**
     * 고를 수 있는 은행 전부. 이름순으로 준다.
     *
     * <p>상대방을 등록할 때 은행을 함께 받아야 하는데, 그 목록을 프런트가 따로 적어 두면
     * 여기서 코드를 하나 고쳤을 때 화면은 옛 코드를 그대로 보낸다. 계좌번호 앞자리로
     * 은행을 추정하지 않기로 한 이상, 사용자가 고를 목록은 백엔드가 준다.
     *
     * <p>{@code 012}(농협중앙회)는 빼놓는다. 사용자에게는 {@code 011}(농협은행)과 똑같이
     * "농협"으로 들려 둘 중 무엇을 고를지 정할 수 없다. 확인 복창에서 이름을 읽을 때만 쓴다.
     */
    public List<Bank> findAll() {
        return DISPLAY_NAME_BY_CODE.entrySet().stream()
                .filter(entry -> !HIDDEN_FROM_SELECTION.contains(entry.getKey()))
                .map(entry -> Bank.of(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Bank::name))
                .toList();
    }

    /** 고를 수 있는 은행 하나. */
    public record Bank(String code, String name) {

        public static Bank of(final String code, final String name) {
            return new Bank(code, name);
        }
    }
}
