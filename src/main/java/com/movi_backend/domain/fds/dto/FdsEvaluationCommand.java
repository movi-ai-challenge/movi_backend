package com.movi_backend.domain.fds.dto;

import java.time.LocalDateTime;

/**
 * FDS 평가에 필요한 입력.
 *
 * <p>이체 도메인이 FDS 도메인에 넘기는 값이다. 엔티티를 그대로 넘기지 않는 이유는 FDS가
 * 계좌번호·수취인 이름 같은 값을 볼 이유가 없기 때문이다. <b>모델에 개인정보를 결합하지 않는다.</b>
 *
 * @param recipientTransferCount 저장된 수취인에게 보낸 횟수. 저장되지 않은 상대면 {@code null}
 * @param trustedDevice          등록된 기기 여부
 * @param sttConfidence          음성 인식 신뢰도 0~1. 화면 조작이면 1.0
 */
public record FdsEvaluationCommand(
        Long transferId,
        Long userId,
        Long amount,
        Long balanceBefore,
        LocalDateTime requestedAt,
        Integer recipientTransferCount,
        boolean trustedDevice,
        double sttConfidence
) {

    public static FdsEvaluationCommand of(
            final Long transferId,
            final Long userId,
            final Long amount,
            final Long balanceBefore,
            final LocalDateTime requestedAt,
            final Integer recipientTransferCount,
            final boolean trustedDevice,
            final double sttConfidence
    ) {
        return new FdsEvaluationCommand(
                transferId,
                userId,
                amount,
                balanceBefore,
                requestedAt,
                recipientTransferCount,
                trustedDevice,
                sttConfidence
        );
    }
}
