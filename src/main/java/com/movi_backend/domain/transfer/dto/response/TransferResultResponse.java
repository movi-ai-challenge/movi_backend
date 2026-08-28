package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;

/**
 * 직접 입력 송금 실행 결과.
 *
 * <p>차단·실패도 200으로 돌려준다. 사용자에게는 "왜 돈이 나가지 않았는지"가 결과이지
 * 요청이 잘못됐다는 뜻이 아니기 때문이다. 상태와 위험도를 함께 주므로 프런트는 완료·차단을
 * 스스로 판단하지 않고 그대로 표시한다.
 */
public record TransferResultResponse(
        Long transferId,
        TransferStatus status,
        RiskLevel riskLevel,
        Long amount,
        String recipientName,
        LocalDateTime completedAt
) {

    public static TransferResultResponse from(final TransferExecutionResult result) {
        return new TransferResultResponse(
                result.transferId(),
                result.status(),
                result.riskLevel(),
                result.amount(),
                result.recipientName(),
                result.completedAt()
        );
    }

    public String toVoiceMessage() {
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
        return "송금을 안전하게 확인하고 있어요.";
    }
}
