package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.BalanceSnapshot;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceInquiryService {

    private final AccountRepository accountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final BalanceInquiryPort balanceInquiryPort;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    @Transactional
    public BalanceResponse inquire(final Long userId, final String accountAlias) {
        final Account account = findAccount(userId, accountAlias);
        return inquireAccount(userId, account);
    }

    @Transactional
    public BalanceResponse inquireAccount(final Long userId, final Account account) {
        final BalanceSnapshot balanceSnapshot = refresh(userId, account);
        return BalanceResponse.of(account, balanceSnapshot);
    }

    @Transactional
    public BalanceSnapshot refresh(final Long userId, final Account account) {
        validateAccount(account, userId);

        final OpenbankingConnection connection = account.getConnection();
        validateConnection(connection, userId);

        final BalanceInquiryResult inquiryResult = inquireBalance(account, connection);
        return saveSnapshot(account, inquiryResult);
    }

    private Account findAccount(final Long userId, final String accountAlias) {
        if (accountAlias == null || accountAlias.isBlank()) {
            return accountRepository.findByUserIdAndPrimaryTrue(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_ACCOUNT_NOT_SET));
        }
        return accountRepository.findByUserIdAndAlias(userId, accountAlias.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateAccount(final Account account, final Long userId) {
        if (!Objects.equals(account.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private void validateConnection(final OpenbankingConnection connection, final Long userId) {
        if (connection == null) {
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }
        if (!Objects.equals(connection.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!connection.isUsable(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONNECTION_EXPIRED);
        }
    }

    private BalanceInquiryResult inquireBalance(
            final Account account,
            final OpenbankingConnection connection
    ) {
        final BalanceInquiryResult inquiryResult;
        try {
            inquiryResult = balanceInquiryPort.inquire(
                    account.getFintechUseNum(),
                    sensitiveDataCrypto.decrypt(connection.getAccessToken())
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED);
        }

        if (inquiryResult == null || !inquiryResult.isValid()) {
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED);
        }
        return inquiryResult;
    }

    private BalanceSnapshot saveSnapshot(
            final Account account,
            final BalanceInquiryResult inquiryResult
    ) {
        final BalanceSnapshot balanceSnapshot = BalanceSnapshot.builder()
                .account(account)
                .balanceAmount(inquiryResult.balanceAmount())
                .availableAmount(inquiryResult.availableAmount())
                .build();
        return balanceSnapshotRepository.save(balanceSnapshot);
    }
}
