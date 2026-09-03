package com.movi_backend.domain.transfer.application.model;

/**
 * 예금주조회로 확인을 마친 송금 대상.
 *
 * <p>이 객체는 <b>확인된 뒤에만</b> 만들어진다. 사용자가 말한 계좌번호나 AI 가 추출한 값이
 * 그대로 여기 담기지 않는다 — 숫자만 남기고, 은행코드와 전체 계좌번호가 실재하는 계좌와
 * 정확히 맞은 다음에야 만들어진다.
 *
 * @param bankCode       은행 코드
 * @param accountNumber  숫자만 남긴 전체 계좌번호
 * @param accountNumHash 계좌 동일성 판단용 HMAC. 같은 사용자·은행·해시는 같은 상대다
 * @param holderName     은행이 확인해 준 예금주명
 */
public record VerifiedTransferTarget(
        String bankCode,
        String accountNumber,
        String accountNumHash,
        String holderName
) {

    public static VerifiedTransferTarget of(
            final String bankCode,
            final String accountNumber,
            final String accountNumHash,
            final String holderName
    ) {
        return new VerifiedTransferTarget(bankCode, accountNumber, accountNumHash, holderName);
    }

    /** 확인 복창에서 읽어 줄 계좌 끝 네 자리. */
    public String lastFourDigits() {
        return accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}
