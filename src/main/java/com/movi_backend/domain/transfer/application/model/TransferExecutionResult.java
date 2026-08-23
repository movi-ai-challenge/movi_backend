package com.movi_backend.domain.transfer.application.model;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.type.TransferStatus;
import java.time.LocalDateTime;

public record TransferExecutionResult(
        Long transferId,
        TransferStatus status,
        RiskLevel riskLevel,
        Long amount,
        String recipientName,
        LocalDateTime completedAt
) {

    public static TransferExecutionResult of(
            final Transfer transfer,
            final FdsAssessment assessment
    ) {
        return new TransferExecutionResult(
                transfer.getId(),
                transfer.getStatus(),
                assessment.getRiskLevel(),
                transfer.getAmount(),
                transfer.getToHolderName(),
                transfer.getCompletedAt()
        );
    }
}
