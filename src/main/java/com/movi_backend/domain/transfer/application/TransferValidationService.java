package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.SensitiveTextMasker;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferValidationService {

    private static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.80");
    private static final BigDecimal MAXIMUM_CONFIDENCE = BigDecimal.ONE;
    private static final String RECIPIENT_QUESTION = "누구에게 보내시겠어요?";
    private static final String AMOUNT_QUESTION = "얼마를 보내시겠어요?";

    private final TransferRecipientRepository transferRecipientRepository;
    private final TransferProperties transferProperties;

    @Transactional(readOnly = true)
    public TransferValidationResult validate(
            final Long userId,
            final TransferCommandRequest command
    ) {
        validateOverallConfidence(command);
        validateDirectAccountNumber(command);

        final List<TransferSlot> missingSlots = findMissingSlots(command);
        if (!missingSlots.isEmpty()) {
            return createClarification(missingSlots);
        }

        validateAmountRange(command.amount());
        final TransferRecipient recipient = findRecipient(userId, command.recipient());
        return ValidatedTransferCommand.of(
                command.amount(),
                recipient,
                normalizeOptional(command.sourceAccountAlias())
        );
    }

    private void validateDirectAccountNumber(final TransferCommandRequest command) {
        if (SensitiveTextMasker.containsSensitiveNumber(command.recipient())) {
            throw new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        if (SensitiveTextMasker.containsSensitiveNumber(command.sourceAccountAlias())) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void validateOverallConfidence(final TransferCommandRequest command) {
        if (!isTrusted(command.sttConfidence()) || !isTrusted(command.intentConfidence())) {
            throw new BusinessException(ErrorCode.LOW_CONFIDENCE);
        }
    }

    private List<TransferSlot> findMissingSlots(final TransferCommandRequest command) {
        final List<TransferSlot> missingSlots = new ArrayList<>();
        if (isBlank(command.recipient()) || !isTrusted(command.recipientConfidence())) {
            missingSlots.add(TransferSlot.RECIPIENT);
        }
        if (command.amount() == null || !isTrusted(command.amountConfidence())) {
            missingSlots.add(TransferSlot.AMOUNT);
        }
        return missingSlots;
    }

    private TransferClarification createClarification(final List<TransferSlot> missingSlots) {
        if (missingSlots.contains(TransferSlot.RECIPIENT)) {
            return TransferClarification.of(missingSlots, RECIPIENT_QUESTION);
        }
        return TransferClarification.of(missingSlots, AMOUNT_QUESTION);
    }

    /**
     * 이체 금액이 정책 범위 안인지 확인한다.
     *
     * <p>직접 입력 송금도 같은 한도를 쓴다. 경로마다 한도를 따로 두면 한쪽만 고쳐 놓고
     * 다른 쪽으로 한도를 넘길 수 있다.
     */
    public void validateAmountRange(final long amount) {
        if (amount < transferProperties.minimumAmount()) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
        if (amount > transferProperties.perTransferLimit()) {
            throw new BusinessException(ErrorCode.AMOUNT_LIMIT_EXCEEDED);
        }
    }

    private TransferRecipient findRecipient(final Long userId, final String recipientNickname) {
        final String normalizedNickname = recipientNickname.trim();
        return transferRecipientRepository.findByUserIdAndNickname(userId, normalizedNickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
    }

    private boolean isTrusted(final BigDecimal confidence) {
        if (confidence == null) {
            return false;
        }
        if (confidence.compareTo(MAXIMUM_CONFIDENCE) > 0) {
            return false;
        }
        return confidence.compareTo(MINIMUM_CONFIDENCE) >= 0;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private String normalizeOptional(final String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
