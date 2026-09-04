package com.movi_backend.domain.account.application.port.dto;

/**
 * 예금주 조회로 <b>확인된</b> 수취 계좌.
 *
 * <p>이 객체가 만들어졌다는 것은 은행코드와 <b>전체 계좌번호</b>가 실재하는 계좌와 정확히
 * 맞았다는 뜻이다. 접두어가 맞았다거나, 마스킹된 번호와 비슷하다거나, 음성 인식이 그렇게
 * 들었다는 것은 근거가 되지 않는다.
 *
 * <p>{@code holderName}은 은행이 알려 준 실제 예금주명이다. 사용자가 부른 이름
 * ("엄마")과는 다른 값이며, 확인 복창은 이 이름을 읽어 준다.
 *
 * @param bankCode      은행 코드
 * @param accountNumber 숫자만 남긴 전체 계좌번호
 * @param holderName    은행이 확인해 준 예금주명
 */
public record VerifiedAccountHolder(
        String bankCode,
        String accountNumber,
        String holderName
) {

    public static VerifiedAccountHolder of(
            final String bankCode,
            final String accountNumber,
            final String holderName
    ) {
        return new VerifiedAccountHolder(bankCode, accountNumber, holderName);
    }
}
