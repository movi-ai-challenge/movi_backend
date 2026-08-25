package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.type.TransactionSource;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;

/**
 * 거래내역 단건 상세.
 *
 * <p><b>상대방 계좌번호는 싣지 않는다.</b> 목록 응답과 같은 판단이다. 음성으로 계좌번호를
 * 읽어 주면 사용자에게 쓸모가 없으면서 주변 사람에게 들린다. 저장도 암호화되어 있어
 * 노출하려면 복호화가 필요한데 얻는 것이 없다.
 */
public record TransactionDetailResponse(
        Long transactionId,
        Long accountId,
        TransactionType type,
        Long amount,
        Long balanceAfter,
        String counterpartyName,
        String category,
        LocalDateTime transactedAt,
        String memo,
        TransactionSource source
) {

    private static final String UNKNOWN_COUNTERPARTY = "이름 없는 거래";

    public static TransactionDetailResponse from(final Transaction transaction) {
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getTranType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyName(),
                transaction.getCategory(),
                transaction.getTranDatetime(),
                transaction.getMemo(),
                transaction.getSource()
        );
    }

    /**
     * 화면을 보지 않고도 거래 하나를 파악할 수 있는 문구.
     *
     * <p>거래 후 잔액까지 읽어 준다. 상세를 따로 여는 이유가 대개 "그래서 남은 게 얼마인가"라서다.
     */
    public String toVoiceMessage() {
        final StringBuilder message = new StringBuilder(
                "%s %s.".formatted(formatDate(), formatTransfer())
        );
        if (this.balanceAfter != null) {
            message.append(" 거래 뒤 잔액은 %s이에요."
                    .formatted(KoreanMoneyFormatter.format(this.balanceAfter)));
        }
        if (this.memo != null && !this.memo.isBlank()) {
            message.append(" 메모는 %s이에요.".formatted(this.memo));
        }
        return message.toString();
    }

    private String formatTransfer() {
        final String formattedAmount = KoreanMoneyFormatter.format(this.amount);
        if (this.type == TransactionType.IN) {
            return "%s 님에게서 %s 받았어요".formatted(counterpartyNameForVoice(), formattedAmount);
        }
        return "%s 님에게 %s 보냈어요".formatted(counterpartyNameForVoice(), formattedAmount);
    }

    private String formatDate() {
        return "%d월 %d일".formatted(
                this.transactedAt.getMonthValue(),
                this.transactedAt.getDayOfMonth()
        );
    }

    private String counterpartyNameForVoice() {
        if (this.counterpartyName == null || this.counterpartyName.isBlank()) {
            return UNKNOWN_COUNTERPARTY;
        }
        return this.counterpartyName;
    }
}
