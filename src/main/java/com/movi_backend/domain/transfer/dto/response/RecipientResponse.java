package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.transfer.entity.TransferRecipient;

/**
 * 등록 수취인 한 명.
 *
 * <p>계좌번호는 마스킹한 값만 내보낸다. 사용자가 "맞는 사람인지" 확인하는 데는 뒤 네 자리면
 * 충분하고, 원문은 화면·로그·브라우저 기록 어디에도 남을 이유가 없다.
 */
public record RecipientResponse(
        Long recipientId,
        String nickname,
        String holderName,
        String bankCode,
        String maskedAccountNumber,
        int transferCount
) {

    public static RecipientResponse of(
            final TransferRecipient recipient,
            final String maskedAccountNumber
    ) {
        return new RecipientResponse(
                recipient.getId(),
                recipient.getNickname(),
                recipient.getHolderName(),
                recipient.getBankCode(),
                maskedAccountNumber,
                recipient.getTransferCount()
        );
    }
}
