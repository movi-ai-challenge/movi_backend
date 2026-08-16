package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferQueryService {

    private final TransferRepository transferRepository;
    private final FdsAssessmentRepository fdsAssessmentRepository;

    @Transactional(readOnly = true)
    public TransferStatusResponse findStatus(
            final Long userId,
            final String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        final Transfer transfer = transferRepository
                .findByIdempotencyKeyAndUserId(idempotencyKey, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        final FdsAssessment assessment = fdsAssessmentRepository
                .findByTransferId(transfer.getId())
                .orElse(null);
        return TransferStatusResponse.of(transfer, assessment);
    }

    private void validateIdempotencyKey(final String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "멱등성 키 누락");
        }
        try {
            UUID.fromString(idempotencyKey);
        } catch (final IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "멱등성 키 형식 오류");
        }
    }
}
