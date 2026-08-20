package com.movi_backend.domain.transfer.dto;

/**
 * 오픈뱅킹 이체 결과.
 *
 * <p>응답이 불명확하면 {@code successful}을 {@code true}로 만들지 않는다.
 * 성공으로 추정해 완료 처리하면 사용자는 보내지 않은 돈을 보냈다고 듣게 된다.
 */
public record OpenBankingTransferResult(
        boolean successful,
        String bankTransactionId
) {
    public static OpenBankingTransferResult success(final String bankTransactionId) {
        return new OpenBankingTransferResult(true, bankTransactionId);
    }

    public static OpenBankingTransferResult failure() {
        return new OpenBankingTransferResult(false, null);
    }
}
