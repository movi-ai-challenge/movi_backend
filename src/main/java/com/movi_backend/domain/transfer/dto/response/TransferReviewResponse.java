package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.transfer.application.model.TransferConfirmation;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;

/**
 * 직접 입력 송금 검토 결과. 아직 돈은 나가지 않았다.
 *
 * <p>프런트는 이 응답의 {@code confirmationId}를 보관하고 UUID 멱등성 키 하나를 만든 뒤,
 * 사용자가 명시적으로 확인해야 실행 요청을 보낸다.
 */
public record TransferReviewResponse(
        String confirmationId,
        FromAccount fromAccount,
        Recipient recipient,
        Long amount,
        LocalDateTime expiresAt
) {

    public static TransferReviewResponse of(
            final TransferConfirmation confirmation,
            final Account fromAccount,
            final String recipientHolderName,
            final String recipientNickname,
            final String recipientBankName,
            final String maskedAccountNumber
    ) {
        return new TransferReviewResponse(
                confirmation.confirmationId(),
                new FromAccount(
                        fromAccount.getId(),
                        fromAccount.getAlias(),
                        fromAccount.getBankName()
                ),
                new Recipient(
                        confirmation.recipientId(),
                        recipientNickname,
                        recipientHolderName,
                        recipientBankName,
                        maskedAccountNumber
                ),
                confirmation.amount(),
                confirmation.expiresAt()
        );
    }

    /** 확인 문장은 서버가 만든다. 화면 문구와 TTS가 다른 금액을 말하면 안 된다. */
    public String toVoiceMessage() {
        return "%s에서 %s에게 %s을 보낼까요?".formatted(
                this.fromAccount.toVoiceName(),
                this.recipient.toVoiceName(),
                KoreanMoneyFormatter.format(this.amount)
        );
    }

    public record FromAccount(Long accountId, String alias, String bankName) {

        private String toVoiceName() {
            if (this.alias == null || this.alias.isBlank()) {
                return this.bankName + " 계좌";
            }
            return this.alias;
        }
    }

    /**
     * 확인 문장에 쓸 수취인. {@code holderName}은 예금주조회로 확인된 이름이다.
     *
     * <p>{@code nickname}은 주소록에 등록한 경우에만 있다. 등록하지 않은 계좌로 한 번
     * 보내는 경우에는 비어 있고, 확인된 예금주와 은행으로 상대를 가리킨다.
     */
    public record Recipient(
            Long recipientId,
            String nickname,
            String holderName,
            String bankName,
            String maskedAccountNumber
    ) {

        private String toVoiceName() {
            if (this.bankName == null || this.bankName.isBlank()) {
                return "%s 님".formatted(this.holderName);
            }
            if (this.nickname == null || this.nickname.isBlank()) {
                return "%s %s 님".formatted(this.bankName, this.holderName);
            }
            return "%s, %s %s 님".formatted(this.nickname, this.bankName, this.holderName);
        }
    }
}
