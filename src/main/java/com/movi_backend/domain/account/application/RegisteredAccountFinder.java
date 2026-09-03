package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입력한 계좌번호가 <b>우리 서비스에 연결된 계좌인지</b> 찾는다.
 *
 * <p>상대방을 이름으로 부르려면 그 이름이 실제 계좌에 묶여 있어야 한다. 아무 번호나 등록하게
 * 두면 "엄마한테 보내줘"가 존재하지 않는 계좌로 향한다. 그래서 등록 시점에 한 번 확인한다.
 *
 * <p><b>완전 일치로 찾을 수 없다.</b> {@code accounts.account_num_masked} 는 이름 그대로
 * 마스킹된 값({@code 123456-**-*****1})이라 사용자가 입력한 실제 계좌번호와 글자가 다르다.
 * 그래서 가려지기 전 앞자리를 접두어로 삼아 맞춘다.
 *
 * <p>짧은 접두어로 남의 계좌를 잡지 않도록 두 가지를 지킨다 — 접두어가 너무 짧은 계좌는 아예
 * 후보로 두지 않고, 후보가 둘 이상이면 등록을 거절한다. 애매한 채로 저장하면 나중에 음성으로
 * 부를 때 엉뚱한 사람에게 돈이 간다.
 *
 * @see com.movi_backend.domain.account.infrastructure.MockDepositAccountResolver 같은 접두어 규칙을 쓴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisteredAccountFinder {

    /** 이보다 짧게 노출된 계좌는 남의 것과 헷갈릴 수 있어 찾지 않는다. */
    private static final int MINIMUM_PREFIX_LENGTH = 6;

    private final AccountRepository accountRepository;

    /**
     * 계좌번호로 연결된 계좌 하나를 찾는다.
     *
     * @param plainAccountNumber 사용자가 입력한 계좌번호. 하이픈·공백이 섞여 있어도 된다
     * @throws BusinessException 찾지 못했거나({@code RECIPIENT_ACCOUNT_NOT_FOUND})
     *                           후보가 둘 이상일 때({@code RECIPIENT_ACCOUNT_AMBIGUOUS})
     */
    @Transactional(readOnly = true)
    public Account findByAccountNumber(final String plainAccountNumber) {
        final String target = digitsOf(plainAccountNumber);
        if (target.length() < MINIMUM_PREFIX_LENGTH) {
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        final List<Account> matched = accountRepository.findAll().stream()
                .filter(Account::isActive)
                .filter(account -> matchesAccountNumber(account, target))
                .toList();

        if (matched.isEmpty()) {
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }
        if (matched.size() > 1) {
            log.debug("[RECIPIENT] 계좌번호가 여러 계좌와 맞아 등록을 거절합니다. 후보={}", matched.size());
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_AMBIGUOUS);
        }
        return matched.get(0);
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
