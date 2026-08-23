package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.dto.response.AccountListResponse;
import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 조회·기본계좌 관리 (명세서 1.3, 1.4).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_ALIAS_LENGTH = 50;

    private final AccountRepository accountRepository;

    /** 연결된 계좌 목록. 기본 계좌가 먼저 온다. */
    @Transactional(readOnly = true)
    public AccountListResponse findAll(final Long userId) {
        final List<Account> accounts =
                accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(userId);
        return AccountListResponse.from(accounts);
    }

    /**
     * 기본 계좌를 지정한다.
     *
     * <p>사용자당 기본 계좌는 최대 1개이므로 기존 것을 먼저 해제한다. 해제를 빠뜨리면
     * 계좌를 지정하지 않은 음성 명령이 어느 쪽을 쓸지 알 수 없게 된다.
     */
    @Transactional
    public AccountResponse designatePrimary(final Long userId, final Long accountId) {
        final Account target = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        accountRepository.findByUserIdAndPrimaryTrue(userId)
                .filter(current -> !current.getId().equals(accountId))
                .ifPresent(Account::releasePrimary);

        target.designateAsPrimary();
        return AccountResponse.from(target);
    }

    /**
     * 음성 별칭을 붙인다. "월급통장에서 보내줘" 같은 명령을 해석하는 근거가 된다.
     *
     * <p>같은 별칭이 둘이면 어느 계좌인지 확정할 수 없으므로 중복을 막는다.
     */
    @Transactional
    public AccountResponse changeAlias(final Long userId, final Long accountId, final String alias) {
        final String normalizedAlias = normalizeAlias(alias);
        final Account target = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        accountRepository.findByUserIdAndAlias(userId, normalizedAlias)
                .filter(duplicated -> !duplicated.getId().equals(accountId))
                .ifPresent(duplicated -> {
                    throw new BusinessException(ErrorCode.ACCOUNT_ALIAS_DUPLICATED);
                });

        target.changeAlias(normalizedAlias);
        return AccountResponse.from(target);
    }

    private String normalizeAlias(final String alias) {
        if (alias == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        final String normalizedAlias = alias.trim();
        if (normalizedAlias.isEmpty() || normalizedAlias.length() > MAX_ALIAS_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return normalizedAlias;
    }
}
