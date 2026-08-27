package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 직접 입력 송금 검토 요청.
 *
 * <p>수취인은 <b>등록된 수취인 ID로만</b> 지정한다. 이름이나 계좌번호를 직접 받으면 프런트가
 * 수취인을 만들어 내는 셈이 되고, 오타 한 번이 모르는 계좌로 가는 이체가 된다.
 *
 * <p>{@code fromAccountId}를 비우면 기본 계좌에서 나간다.
 */
public record TransferReviewRequest(
        @NotNull
        Long recipientId,

        @NotNull
        @Positive
        Long amount,

        Long fromAccountId
) {
}
