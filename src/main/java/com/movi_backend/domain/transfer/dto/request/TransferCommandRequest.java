package com.movi_backend.domain.transfer.dto.request;

import java.math.BigDecimal;

/**
 * 이체 명령 한 건.
 *
 * <p>수취인을 <b>두 가지 방법</b>으로 지정한다 — 등록해 둔 이름({@code recipient})이거나,
 * 발화에서 받은 계좌번호({@code accountNumber} + {@code bankCode})다. 둘 중 하나만 있으면
 * 된다. 계좌번호로 받은 경우 확인 단계에서 자릿수를 하나씩 읽어 준 뒤 실행한다.
 */
public record TransferCommandRequest(
        Long amount,
        String recipient,
        String accountNumber,
        String bankCode,
        String sourceAccountAlias,
        BigDecimal sttConfidence,
        BigDecimal intentConfidence,
        BigDecimal amountConfidence,
        BigDecimal recipientConfidence
) {

    /** 등록된 이름으로 보낼 때. */
    public static TransferCommandRequest of(
            final Long amount,
            final String recipient,
            final String sourceAccountAlias,
            final BigDecimal sttConfidence,
            final BigDecimal intentConfidence,
            final BigDecimal amountConfidence,
            final BigDecimal recipientConfidence
    ) {
        return new TransferCommandRequest(
                amount,
                recipient,
                null,
                null,
                sourceAccountAlias,
                sttConfidence,
                intentConfidence,
                amountConfidence,
                recipientConfidence
        );
    }

    /** 계좌번호를 직접 말한 경우까지 포함해 만든다. */
    public static TransferCommandRequest of(
            final Long amount,
            final String recipient,
            final String accountNumber,
            final String bankCode,
            final String sourceAccountAlias,
            final BigDecimal sttConfidence,
            final BigDecimal intentConfidence,
            final BigDecimal amountConfidence,
            final BigDecimal recipientConfidence
    ) {
        return new TransferCommandRequest(
                amount,
                recipient,
                accountNumber,
                bankCode,
                sourceAccountAlias,
                sttConfidence,
                intentConfidence,
                amountConfidence,
                recipientConfidence
        );
    }

    /** 계좌번호를 말해 준 경우인지. 등록된 이름이 없어도 이체할 수 있다. */
    public boolean hasSpokenAccount() {
        return accountNumber != null && !accountNumber.isBlank()
                && bankCode != null && !bankCode.isBlank();
    }
}
