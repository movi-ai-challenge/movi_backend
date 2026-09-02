package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.dto.response.TransactionDetailResponse;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
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
    private final FdsAssessmentRepository fdsAssessmentRepository;
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
        final Map<Long, RiskLevel> riskLevels = findRiskLevels(transactions.getContent());
        return PageResponse.of(
                transactions.getContent().stream()
                        .map(transaction -> TransactionResponse.from(
                                transaction,
                                riskLevels.get(transaction.getId())
                        ))
                        .toList(),
                page,
                size,
                transactions.getTotalElements()
        );
    }

    /**
     * 목록에 실린 거래들의 FDS 판정을 한 번에 읽는다.
     *
     * <p>거래마다 따로 조회하면 목록 길이만큼 질의가 나간다. 이체를 거치지 않은 거래는
     * 평가가 없으므로 결과에서 빠지고, 화면은 위험 표시를 하지 않는다.
     */
    private Map<Long, RiskLevel> findRiskLevels(final List<Transaction> transactions) {
        final Map<Long, Long> transferIdByTransaction = new LinkedHashMap<>();
        transactions.forEach(transaction -> {
            if (transaction.getTransfer() != null) {
                transferIdByTransaction.put(transaction.getId(), transaction.getTransfer().getId());
            }
        });
        if (transferIdByTransaction.isEmpty()) {
            return Map.of();
        }

        final Map<Long, RiskLevel> riskByTransfer = fdsAssessmentRepository
                .findByTransferIdIn(List.copyOf(transferIdByTransaction.values()))
                .stream()
                .collect(Collectors.toMap(
                        assessment -> assessment.getTransfer().getId(),
                        FdsAssessment::getRiskLevel,
                        (first, second) -> first
                ));

        final Map<Long, RiskLevel> result = new LinkedHashMap<>();
        transferIdByTransaction.forEach((transactionId, transferId) -> {
            final RiskLevel riskLevel = riskByTransfer.get(transferId);
            if (riskLevel != null) {
                result.put(transactionId, riskLevel);
            }
        });
        return result;
    }

    /**
     * 거래 1건을 조회한다.
     *
     * <p>거래는 계좌에 매달려 있고 계좌는 사용자에 매달려 있다. 다른 사람의 거래 ID를
     * 넣어도 열리지 않도록 계좌 소유자까지 확인한다.
     */
    @Transactional(readOnly = true)
    public TransactionDetailResponse findOne(final Long userId, final Long transactionId) {
        final Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (!Objects.equals(transaction.getAccount().getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        return TransactionDetailResponse.from(transaction);
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
