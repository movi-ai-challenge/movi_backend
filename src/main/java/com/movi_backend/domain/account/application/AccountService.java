package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.dto.response.AccountListResponse;
import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 조회·기본계좌 관리 (명세서 1.3, 1.4).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_ALIAS_LENGTH = 50;

    /** 아직 결과가 정해지지 않은 이체 상태. 이 상태의 이체가 걸린 계좌는 해제하지 않는다. */
    private static final Set<TransferStatus> IN_FLIGHT_TRANSFER_STATUSES =
            Set.of(TransferStatus.PENDING, TransferStatus.RISK_REVIEW);

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

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

        try {
            target.changeAlias(normalizedAlias);
            accountRepository.flush();
        } catch (final DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALIAS_DUPLICATED);
        }
        return AccountResponse.from(target);
    }

    /**
     * 계좌 연결을 해제한다 (명세서 1.5).
     *
     * <p>행을 지우지 않고 {@code is_active}만 내린다. 이 계좌를 참조하는 거래내역과 이체 이력이
     * 남아 있어야 하고, 지난 이체를 되짚을 수 없게 되면 분쟁이 났을 때 근거가 사라진다.
     *
     * <p>기본 계좌를 해제하면 남은 계좌 중 하나를 기본으로 올린다. 기본 계좌가 비면 계좌를
     * 지정하지 않은 음성 명령("잔액 알려줘")이 어느 계좌를 볼지 알 수 없어진다.
     *
     * @return 해제 후 남은 계좌 목록
     */
    @Transactional
    public AccountListResponse disconnect(final Long userId, final Long accountId) {
        final Account target = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        if (transferRepository.existsByFromAccountIdAndStatusIn(accountId, IN_FLIGHT_TRANSFER_STATUSES)) {
            throw new BusinessException(ErrorCode.ACCOUNT_HAS_PENDING_TRANSFER);
        }

        final boolean wasPrimary = target.isPrimary();
        target.deactivate();

        final List<Account> remaining =
                accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(userId);

        if (wasPrimary) {
            promoteNextPrimary(remaining);
        }
        return AccountListResponse.from(remaining);
    }

    /**
     * 기본 계좌가 사라진 자리를 메운다. 남은 계좌가 없으면 아무것도 하지 않는다 —
     * 마지막 계좌까지 해제하는 것은 사용자의 선택이므로 막지 않는다.
     */
    private void promoteNextPrimary(final List<Account> remaining) {
        if (remaining.isEmpty()) {
            return;
        }
        remaining.get(0).designateAsPrimary();
    }

    private String normalizeAlias(final String alias) {
        if (alias == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        final String normalizedAlias = alias.strip();
        if (normalizedAlias.isEmpty() || normalizedAlias.length() > MAX_ALIAS_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return normalizedAlias;
    }
}
