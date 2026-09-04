package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime completedAt,
        /** FDS 가 짚은 근거. 위험도가 LOW 여도 알려 줄 값어치가 있다. */
        List<String> riskReasons
) {

    public static TransferResultResponse from(final TransferExecutionResult result) {
        return new TransferResultResponse(
                result.transferId(),
                result.status(),
                result.riskLevel(),
                result.amount(),
                result.recipientName(),
                result.completedAt(),
                result.riskReasons()
        );
    }

    /**
     * 위험 근거를 뒤에 덧붙인다.
     *
     * <p>화면을 보지 않는 사용자는 이 문장만 듣는다. 위험도가 LOW 라도 "처음 보내는
     * 계좌"였다는 사실은 알려 줘야, 잘못 보냈을 때 곧바로 알아차릴 수 있다.
     */
    private String withRiskReasons(final String message) {
        if (this.riskReasons == null || this.riskReasons.isEmpty()) {
            return message;
        }
        return message + " " + String.join(", ", this.riskReasons) + ".";
    }

    public String toVoiceMessage() {
        if (this.status == TransferStatus.COMPLETED) {
            return withRiskReasons("%s 님에게 %s을 보냈어요.".formatted(
                    this.recipientName,
                    KoreanMoneyFormatter.format(this.amount)
            ));
        }
        if (this.status == TransferStatus.BLOCKED) {
            // 막힌 이유를 함께 말한다. "위험해서 막았어요"만으로는 사용자가
            // 무엇을 고쳐 다시 시도해야 할지 알 수 없다.
            return withRiskReasons(ErrorCode.HIGH_RISK_BLOCKED.getVoiceMessage());
        }
        if (this.status == TransferStatus.FAILED) {
            return ErrorCode.TRANSFER_EXECUTION_FAILED.getVoiceMessage();
        }
        return "은행의 송금 결과를 확인하고 있어요. 다시 송금하지 마세요.";
    }
}
