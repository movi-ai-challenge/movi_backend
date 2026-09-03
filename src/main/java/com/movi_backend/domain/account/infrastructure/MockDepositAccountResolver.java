package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대역 이체에서 <b>받는 쪽이 우리 서비스 사용자인지</b> 찾는다.
 *
 * <p>실제 오픈뱅킹이라면 상대 은행이 입금하지만 대역에는 그 상대가 없다. 보내는 사람과 받는
 * 사람이 둘 다 우리 사용자인 시연에서는 받는 쪽 잔액도 늘어야 하는데, 그러려면 이체 명령의
 * 은행코드·계좌번호를 우리 {@code accounts} 의 핀테크이용번호로 바꿔야 한다.
 *
 * <p><b>완전 일치로 찾을 수 없다.</b> {@code accounts.account_num_masked} 는 이름 그대로
 * 마스킹된 값({@code 3522315749***})이라 이체 명령이 들고 오는 실제 계좌번호와 글자가 다르다.
 * 그래서 마스킹되지 않은 앞자리를 접두어로 삼아 은행코드와 함께 맞춘다.
 *
 * <p>짧은 접두어로 남의 계좌에 입금하지 않도록 두 가지를 지킨다 — 접두어가 너무 짧으면 아예
 * 찾지 않고, 후보가 둘 이상이면 포기한다. 못 찾으면 입금을 건너뛸 뿐 이체는 그대로 끝난다.
 * 이 해석은 대역에만 있고 실제 오픈뱅킹 경로는 거치지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDepositAccountResolver {

    /** 이보다 짧게 노출된 계좌는 남의 것과 헷갈릴 수 있어 찾지 않는다. */
    private static final int MINIMUM_PREFIX_LENGTH = 6;

    /**
     * 같은 은행으로 볼 코드 묶음.
     *
     * <p>농협은 은행 코드가 둘이다 — {@code 011}(농협은행)과 {@code 012}(농협중앙회·단위농협).
     * 사용자는 둘 다 "농협"이라고 부르고, 어느 쪽인지 말로 구분할 방법이 없다. 발화에서
     * 받은 코드와 계좌에 저장된 코드가 이 묶음 안에서 갈리면 같은 은행으로 본다.
     *
     * <p>계좌번호 접두어까지 맞아야 하므로 이 완화로 남의 계좌가 걸릴 여지는 거의 없다.
     * 후보가 둘 이상이면 어차피 포기한다.
     */
    private static final List<Set<String>> SAME_BANK_CODES = List.of(
            Set.of("011", "012")
    );

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Optional<String> resolveFintechUseNum(
            final String toBankCode,
            final String toAccountNum
    ) {
        final String target = digitsOf(toAccountNum);
        if (toBankCode == null || toBankCode.isBlank() || target.isEmpty()) {
            return Optional.empty();
        }

        final List<Account> matched = accountRepository.findAll().stream()
                .filter(Account::isActive)
                .filter(account -> isSameBank(toBankCode, account.getBankCode()))
                .filter(account -> matchesAccountNumber(account, target))
                .toList();

        if (matched.size() != 1) {
            log.debug(
                    "[MOCK-DEPOSIT] 받는 계좌를 특정하지 못해 입금을 건너뜁니다. 후보={}",
                    matched.size()
            );
            return Optional.empty();
        }
        return Optional.ofNullable(matched.get(0).getFintechUseNum());
    }

    private boolean isSameBank(final String spokenCode, final String accountCode) {
        if (spokenCode.equals(accountCode)) {
            return true;
        }
        for (final Set<String> family : SAME_BANK_CODES) {
            if (family.contains(spokenCode) && family.contains(accountCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAccountNumber(final Account account, final String target) {
        final String prefix = visiblePrefixOf(account.getAccountNumMasked());
        if (prefix.length() < MINIMUM_PREFIX_LENGTH) {
            return false;
        }
        return target.startsWith(prefix);
    }

    /**
     * 마스킹 문자가 나오기 전까지의 숫자.
     *
     * <p>{@code 3522315749***} 면 {@code 3522315749}, {@code 123456-**-*****1} 이면
     * {@code 123456} 이다. 가운데가 가려진 뒤의 숫자는 자리를 알 수 없어 쓰지 않는다.
     */
    private String visiblePrefixOf(final String maskedAccountNumber) {
        if (maskedAccountNumber == null) {
            return "";
        }
        final StringBuilder prefix = new StringBuilder();
        for (final char character : maskedAccountNumber.toCharArray()) {
            if (character == '*') {
                break;
            }
            if (Character.isDigit(character)) {
                prefix.append(character);
            }
        }
        return prefix.toString();
    }

    private String digitsOf(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
