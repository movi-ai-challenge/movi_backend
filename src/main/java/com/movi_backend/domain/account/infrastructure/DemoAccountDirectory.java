package com.movi_backend.domain.account.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 시연·개발 대역이 아는 계좌 명부.
 *
 * <p><b>왜 필요한가.</b> 우리 {@code accounts} 테이블에는 마스킹된 번호
 * ({@code 123456-**-*****1})만 있고 전체 계좌번호가 없다. 전체 번호를 정확히 대조하려면
 * 어딘가에 전체 번호가 있어야 하는데, 실제로는 은행(예금주조회 API)이 그 역할을 한다.
 * 그 은행이 없는 시연 환경에서 은행 대신 답해 주는 것이 이 명부다.
 *
 * <p><b>실제 환경에서는 로드되지 않는다.</b> {@code movi.openbanking.transfer-mode=mock}
 * 일 때만 빈으로 올라간다. 시연용으로 적어 둔 계좌가 실제 송금의 검증 근거가 되면, 여기
 * 없는 진짜 계좌는 막히고 여기 적힌 번호는 확인 없이 통과한다.
 *
 * <p>조회는 <b>은행코드와 전체 계좌번호가 모두 정확히 같을 때만</b> 맞는다. 접두어·부분
 * 일치를 두지 않는다 — 대역이라고 규칙을 느슨하게 두면 시연에서는 통과하고 실제로는
 * 막히는 흐름이 생겨, 무엇이 맞는 동작인지 아무도 모르게 된다.
 *
 * <p>{@code fintechUseNum}이 있는 항목은 <b>우리 서비스 사용자의 계좌</b>다. 대역 이체가
 * 입금할 대상을 찾을 때 쓴다. 외부 수취인은 입금할 곳이 없으므로 비어 있다.
 */
@Component
@ConditionalOnProperty(
        name = "movi.openbanking.transfer-mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class DemoAccountDirectory {

    /**
     * 시연 계좌 한 건.
     *
     * @param bankCode      은행 코드
     * @param accountNumber 전체 계좌번호 (숫자만)
     * @param holderName    예금주명
     * @param fintechUseNum 우리 사용자 계좌면 핀테크이용번호, 외부 수취인이면 {@code null}
     */
    public record DemoAccount(
            String bankCode,
            String accountNumber,
            String holderName,
            String fintechUseNum
    ) {

        public static DemoAccount ours(
                final String bankCode,
                final String accountNumber,
                final String holderName,
                final String fintechUseNum
        ) {
            return new DemoAccount(bankCode, accountNumber, holderName, fintechUseNum);
        }

        public static DemoAccount external(
                final String bankCode,
                final String accountNumber,
                final String holderName
        ) {
            return new DemoAccount(bankCode, accountNumber, holderName, null);
        }
    }

    /** 우리 서비스 사용자의 계좌 — 시드가 만드는 {@code accounts} 와 짝이 맞아야 한다. */
    public static final DemoAccount DEMO_USER_CHECKING =
            DemoAccount.ours("004", "12345678901231", "김철수", "199000000000000000000001");
    public static final DemoAccount DEMO_USER_SAVING =
            DemoAccount.ours("088", "110333444522", "김철수", "199000000000000000000002");
    public static final DemoAccount OTHER_USER_CHECKING =
            DemoAccount.ours("011", "35211112299", "이순자", "199000000000000000000901");

    /** 외부 수취인 — 시드가 만드는 {@code transfer_recipients} 와 짝이 맞아야 한다. */
    public static final DemoAccount RECIPIENT_MOTHER =
            DemoAccount.external("088", "110123456789", "이영자");
    public static final DemoAccount RECIPIENT_SON =
            DemoAccount.external("004", "004987654321", "김민수");
    public static final DemoAccount RECIPIENT_FIRST_TIME =
            DemoAccount.external("020", "020112233445", "김영희");
    public static final DemoAccount RECIPIENT_DAUGHTER =
            DemoAccount.external("004", "004555666777", "이미영");

    private static final List<DemoAccount> ACCOUNTS = List.of(
            DEMO_USER_CHECKING,
            DEMO_USER_SAVING,
            OTHER_USER_CHECKING,
            RECIPIENT_MOTHER,
            RECIPIENT_SON,
            RECIPIENT_FIRST_TIME,
            RECIPIENT_DAUGHTER
    );

    private final Map<String, DemoAccount> byBankAndAccount = index();

    /** 은행코드와 전체 계좌번호가 정확히 일치하는 계좌. */
    public Optional<DemoAccount> find(final String bankCode, final String accountNumber) {
        if (bankCode == null || accountNumber == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byBankAndAccount.get(keyOf(bankCode.trim(), accountNumber)));
    }

    private static Map<String, DemoAccount> index() {
        final Map<String, DemoAccount> index = new LinkedHashMap<>();
        for (final DemoAccount account : ACCOUNTS) {
            index.put(keyOf(account.bankCode(), account.accountNumber()), account);
        }
        return Map.copyOf(index);
    }

    private static String keyOf(final String bankCode, final String accountNumber) {
        return bankCode + ":" + accountNumber;
    }
}
