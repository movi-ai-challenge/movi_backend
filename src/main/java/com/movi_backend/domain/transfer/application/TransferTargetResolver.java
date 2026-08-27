package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이체 대상(출금 계좌·수취인)의 소유권 확인.
 *
 * <p>음성과 직접 입력이 같은 규칙으로 계좌와 수취인을 찾도록 한 곳에 모았다. 두 경로가
 * 각자 조회하면 한쪽에만 소유권 검사가 빠지는 사고가 난다 — 남의 계좌에서 돈이 나간다.
 */
@Component
@RequiredArgsConstructor
public class TransferTargetResolver {

    private final AccountRepository accountRepository;
    private final TransferRecipientRepository transferRecipientRepository;

    /** 별칭이 없으면 기본 계좌를 쓴다. 음성에서 출금 계좌를 말하지 않은 경우다. */
    @Transactional(readOnly = true)
    public Account resolveSourceAccount(final Long userId, final String accountAlias) {
        if (accountAlias == null || accountAlias.isBlank()) {
            return requireActive(accountRepository.findByUserIdAndPrimaryTrue(userId)
                    .orElseThrow(() ->
                            new BusinessException(ErrorCode.PRIMARY_ACCOUNT_NOT_SET)));
        }
        return requireActive(accountRepository
                .findByUserIdAndAlias(userId, accountAlias.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public Account resolveOwnedAccount(final Long userId, final Long accountId) {
        final Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!Objects.equals(account.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return requireActive(account);
    }

    @Transactional(readOnly = true)
    public TransferRecipient resolveOwnedRecipient(final Long userId, final Long recipientId) {
        final TransferRecipient recipient = transferRecipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!Objects.equals(recipient.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return recipient;
    }

    private Account requireActive(final Account account) {
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
    }
}
