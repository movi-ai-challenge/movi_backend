package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;

public record TransferStatusResponse(
        Long transferId,
        TransferStatus status,
        RiskLevel riskLevel,
        Long amount,
        String recipientName,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {

    public static TransferStatusResponse of(
            final Transfer transfer,
            final FdsAssessment assessment
    ) {
        RiskLevel riskLevel = null;
        if (assessment != null) {
            riskLevel = assessment.getRiskLevel();
        }
        return new TransferStatusResponse(
                transfer.getId(),
                transfer.getStatus(),
                riskLevel,
                transfer.getAmount(),
                transfer.getToHolderName(),
                transfer.getRequestedAt(),
                transfer.getCompletedAt()
        );
    }

    public String toVoiceMessage() {
        if (this.status == TransferStatus.PENDING
                || this.status == TransferStatus.RISK_REVIEW) {
            return "송금을 안전하게 확인하고 있어요.";
        }
        if (this.status == TransferStatus.COMPLETED) {
            return "%s 님에게 %s을 보냈어요.".formatted(
                    this.recipientName,
                    KoreanMoneyFormatter.format(this.amount)
            );
        }
        if (this.status == TransferStatus.BLOCKED) {
            return ErrorCode.HIGH_RISK_BLOCKED.getVoiceMessage();
        }
        if (this.status == TransferStatus.FAILED) {
            return ErrorCode.TRANSFER_EXECUTION_FAILED.getVoiceMessage();
        }
        return "송금을 취소했어요.";
    }
}
