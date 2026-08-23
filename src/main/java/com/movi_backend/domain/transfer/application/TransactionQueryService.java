package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> findAll(
            final Long userId,
            final Long accountId,
            final LocalDate startDate,
            final LocalDate endDate,
            final TransactionType type,
            final int page,
            final int size
    ) {
        validateRequest(startDate, endDate, page, size);
        final Account account = findAccount(userId, accountId);
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        final LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        final LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        final PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("tranDatetime"), Sort.Order.desc("id"))
        );
        final Page<Transaction> transactions = transactionRepository.findHistory(
                account.getId(),
                type,
                startAt,
                endAt,
                pageable
        );
        return PageResponse.of(
                transactions.getContent().stream().map(TransactionResponse::from).toList(),
                page,
                size,
                transactions.getTotalElements()
        );
    }

    private Account findAccount(final Long userId, final Long accountId) {
        if (accountId == null) {
            return accountRepository.findByUserIdAndPrimaryTrue(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_ACCOUNT_NOT_SET));
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateRequest(
            final LocalDate startDate,
            final LocalDate endDate,
            final int page,
            final int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "페이지 범위 오류");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "조회 기간 오류");
        }
        if (endDate != null && endDate.equals(LocalDate.MAX)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "조회 종료일 범위 오류");
        }
    }
}
