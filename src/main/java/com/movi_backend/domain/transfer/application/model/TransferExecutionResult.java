package com.movi_backend.domain.transfer.application.model;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.fds.application.RiskReasonNarrator;
import java.time.LocalDateTime;
import java.util.List;

public record TransferExecutionResult(
        Long transferId,
        TransferStatus status,
        RiskLevel riskLevel,
        Long amount,
        String recipientName,
        LocalDateTime completedAt,
        /**
         * FDS 가 짚은 근거를 사람이 알아들을 말로 바꾼 것.
         *
         * <p>위험도가 LOW 여도 "처음 보내는 계좌"라는 사실은 알려 줄 값어치가 있다.
         * 화면을 보지 않는 사용자에게는 이 문장이 돈을 보내기 전 마지막 판단 근거다.
         */
        List<String> riskReasons
) {

    public TransferExecutionResult {
        riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
    }

    public static TransferExecutionResult of(
            final Transfer transfer,
            final FdsAssessment assessment,
            final List<String> reasonCodes
    ) {
        return new TransferExecutionResult(
                transfer.getId(),
                transfer.getStatus(),
                assessment.getRiskLevel(),
                transfer.getAmount(),
                transfer.getToHolderName(),
                transfer.getCompletedAt(),
                RiskReasonNarrator.describe(reasonCodes)
        );
    }
}
