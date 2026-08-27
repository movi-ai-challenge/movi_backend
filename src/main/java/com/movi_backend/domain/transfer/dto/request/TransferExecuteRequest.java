package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 직접 입력 송금 실행 요청.
 *
 * <p>금액·수취인·출금 계좌를 다시 받지 않는다. 서버가 검토 시점의 스냅샷을 들고 있으므로
 * 이 요청은 "그 확인을 실행한다"는 뜻만 갖는다. 실행 요청이 금액을 다시 실어 보낼 수 있으면
 * 사용자가 검토한 내용과 다른 금액이 나갈 수 있다.
 *
 * <p>{@code idempotencyKey}는 프런트가 검토 응답을 받은 시점에 하나 만들고, 타임아웃 재시도와
 * 상태 조회에도 같은 값을 쓴다.
 */
public record TransferExecuteRequest(
        @NotBlank
        String confirmationId,

        @NotBlank
        String idempotencyKey
) {
}
