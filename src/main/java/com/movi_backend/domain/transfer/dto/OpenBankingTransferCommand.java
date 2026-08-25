package com.movi_backend.domain.transfer.dto;

/**
 * 오픈뱅킹 출금이체 요청 값.
 *
 * <p>{@code toAccountNum}은 평문이다. Provider 호출에 필요해 잠깐 메모리에만 존재하며,
 * <b>로그·예외 메시지·응답 어디에도 남기지 않는다.</b>
 */
public record OpenBankingTransferCommand(
        Long transferId,
        String fromFintechUseNum,
        String toBankCode,
        String toAccountNum,
        String toHolderName,
        Long amount
) {
    public static OpenBankingTransferCommand of(
            final Long transferId,
            final String fromFintechUseNum,
            final String toBankCode,
            final String toAccountNum,
            final String toHolderName,
            final Long amount
    ) {
        return new OpenBankingTransferCommand(
                transferId, fromFintechUseNum, toBankCode, toAccountNum, toHolderName, amount);
    }
}
