package com.movi_backend.domain.transfer.dto;

import java.time.LocalDateTime;

/**
 * 저장된 이체 요청과, 외부 연동에 필요한 값 묶음.
 *
 * <p>{@code toAccountNum}은 평문이다. 오픈뱅킹 호출에 필요해 트랜잭션 밖으로 잠깐 나가지만
 * <b>로그·응답·예외 메시지에 담지 않는다.</b>
 *
 * @param recipientTransferCount 저장된 수취인에게 보낸 횟수. 직접 입력 이체면 {@code null}
 */
public record PreparedTransfer(
        Long transferId,
        Long userId,
        Long amount,
        LocalDateTime requestedAt,
        String fromFintechUseNum,
        String toBankCode,
        String toAccountNum,
        String toHolderName,
        Integer recipientTransferCount
) {
}
