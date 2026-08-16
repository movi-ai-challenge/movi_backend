package com.movi_backend.domain.voice.dto.response;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;
import java.util.List;

public record VoiceCommandResponse(
        Long voiceSessionId,
        VoiceSessionStatus state,
        VoiceIntent intent,
        List<TransferSlot> missingSlots,
        String confirmationId,
        FromAccount fromAccount,
        Recipient recipient,
        Long amount,
        LocalDateTime expiresAt
) {

    private static final String RECIPIENT_QUESTION = "누구에게 보내시겠어요?";
    private static final String AMOUNT_QUESTION = "얼마를 보내시겠어요?";

    public VoiceCommandResponse {
        if (missingSlots == null) {
            missingSlots = List.of();
        } else {
            missingSlots = List.copyOf(missingSlots);
        }
    }

    public static VoiceCommandResponse clarifying(
            final VoiceSession session,
            final List<TransferSlot> missingSlots
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.TRANSFER,
                missingSlots,
                null,
                null,
                null,
                null,
                session.getExpiresAt()
        );
    }

    public static VoiceCommandResponse awaitingConfirmation(
            final VoiceSession session,
            final String confirmationId,
            final Account account,
            final TransferRecipient transferRecipient,
            final long amount
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.TRANSFER,
                List.of(),
                confirmationId,
                FromAccount.from(account),
                Recipient.from(transferRecipient),
                amount,
                session.getExpiresAt()
        );
    }

    public String toVoiceMessage() {
        if (this.state == VoiceSessionStatus.CLARIFYING) {
            if (this.missingSlots.contains(TransferSlot.RECIPIENT)) {
                return RECIPIENT_QUESTION;
            }
            return AMOUNT_QUESTION;
        }
        final String accountName = this.fromAccount.toVoiceName();
        final String formattedAmount = KoreanMoneyFormatter.format(this.amount);
        return "%s에서 %s 님에게 %s을 보낼까요?".formatted(
                accountName,
                this.recipient.holderName(),
                formattedAmount
        );
    }

    public record FromAccount(Long accountId, String alias, String bankName) {

        public static FromAccount from(final Account account) {
            return new FromAccount(account.getId(), account.getAlias(), account.getBankName());
        }

        private String toVoiceName() {
            if (this.alias == null || this.alias.isBlank()) {
                return this.bankName + " 계좌";
            }
            return this.alias;
        }
    }

    /** 암호화된 계좌번호는 복호화·마스킹 계층이 준비되기 전까지 공개하지 않는다. */
    public record Recipient(Long recipientId, String holderName, String bankCode) {

        public static Recipient from(final TransferRecipient recipient) {
            return new Recipient(
                    recipient.getId(),
                    recipient.getHolderName(),
                    recipient.getBankCode()
            );
        }
    }
}
