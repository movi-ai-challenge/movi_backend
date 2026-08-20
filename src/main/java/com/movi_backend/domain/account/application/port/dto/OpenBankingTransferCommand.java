package com.movi_backend.domain.account.application.port.dto;

/**
 * 이체 요청.
 *
 * <p>{@code tranId}는 오픈뱅킹 쪽 중복 차단 키다. 서비스의 {@code idempotency_key}와
 * 짝을 이뤄, 재시도해도 이체가 두 번 나가지 않게 한다.
 *
 * @param tranId             거래 고유번호 (멱등 키)
 * @param fromFintechUseNum  출금 계좌 핀테크이용번호
 * @param toBankCode         입금 은행 코드
 * @param toAccountNum       입금 계좌번호
 * @param toHolderName       입금 예금주명
 * @param amount             이체 금액
 */
public record OpenBankingTransferCommand(
        String tranId,
        String fromFintechUseNum,
        String toBankCode,
        String toAccountNum,
        String toHolderName,
        Long amount
) {
    public static OpenBankingTransferCommand of(
            final String tranId,
            final String fromFintechUseNum,
            final String toBankCode,
            final String toAccountNum,
            final String toHolderName,
            final Long amount
    ) {
        return new OpenBankingTransferCommand(
                tranId, fromFintechUseNum, toBankCode, toAccountNum, toHolderName, amount);
    }
}
