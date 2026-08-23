package com.movi_backend.domain.account.application.port.dto;

import java.time.LocalDateTime;

/**
 * 이체 실행 결과.
 *
 * <p>실패는 이 타입으로 표현하지 않는다. 구현체가 {@code BusinessException}을 던지므로
 * 이 객체가 반환됐다면 이체는 성공한 것이다.
 *
 * @param bankTranId    은행 거래고유번호. 사후 조회·정산 대조에 쓴다
 * @param tranDateTime  거래 일시
 * @param balanceAfter  이체 후 잔액
 */
public record OpenBankingTransferResult(
        String bankTranId,
        LocalDateTime tranDateTime,
        Long balanceAfter
) {
    public static OpenBankingTransferResult of(
            final String bankTranId,
            final LocalDateTime tranDateTime,
            final Long balanceAfter
    ) {
        return new OpenBankingTransferResult(bankTranId, tranDateTime, balanceAfter);
    }
}
