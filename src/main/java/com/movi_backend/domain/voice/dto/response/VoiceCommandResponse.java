package com.movi_backend.domain.voice.dto.response;

import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.KoreanMoneyFormatter;
import java.time.LocalDateTime;
import java.util.List;

public record VoiceCommandResponse(
        Long voiceSessionId,
        VoiceSessionStatus state,
        VoiceIntent intent,
        String transcript,
        List<TransferSlot> missingSlots,
        String confirmationId,
        FromAccount fromAccount,
        Recipient recipient,
        Long amount,
        LocalDateTime expiresAt,
        Long transferId,
        TransferStatus status,
        RiskLevel riskLevel,
        LocalDateTime completedAt,
        History history,
        BalanceResponse balance,
        /** FDS 가 짚은 근거. 위험도가 LOW 여도 알려 줄 값어치가 있다. */
        List<String> riskReasons
) {

    private static final String RECIPIENT_QUESTION = "누구에게 보내시겠어요?";
    private static final String AMOUNT_QUESTION = "얼마를 보내시겠어요?";
    private static final String CANCELED_MESSAGE = "송금을 취소했어요.";

    public VoiceCommandResponse {
        if (missingSlots == null) {
            missingSlots = List.of();
        } else {
            missingSlots = List.copyOf(missingSlots);
        }
    }

    public static VoiceCommandResponse clarifying(
            final VoiceSession session,
            final List<TransferSlot> missingSlots,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.TRANSFER,
                transcript,
                missingSlots,
                null,
                null,
                null,
                null,
                session.getExpiresAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    public static VoiceCommandResponse awaitingConfirmation(
            final VoiceSession session,
            final String confirmationId,
            final Account account,
            final TransferRecipient transferRecipient,
            final long amount,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.TRANSFER,
                transcript,
                List.of(),
                confirmationId,
                FromAccount.from(account),
                Recipient.from(transferRecipient),
                amount,
                session.getExpiresAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    public static VoiceCommandResponse canceled(
            final VoiceSession session,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.CANCEL,
                transcript,
                List.of(),
                null,
                null,
                null,
                null,
                session.getExpiresAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    /** 거래내역 조회 결과. 세션은 이어서 다른 명령을 받을 수 있도록 열어 둔다. */
    public static VoiceCommandResponse history(
            final VoiceSession session,
            final History history,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.HISTORY,
                transcript,
                List.of(),
                null,
                null,
                null,
                null,
                session.getExpiresAt(),
                null,
                null,
                null,
                null,
                history,
                null,
                List.of()
        );
    }

    public static VoiceCommandResponse executed(final TransferExecutionResult result) {
        return executed(result, null);
    }

    public static VoiceCommandResponse executed(
            final TransferExecutionResult result,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                null,
                VoiceSessionStatus.COMPLETED,
                VoiceIntent.CONFIRM,
                transcript,
                List.of(),
                null,
                null,
                Recipient.named(result.recipientName()),
                result.amount(),
                null,
                result.transferId(),
                result.status(),
                result.riskLevel(),
                result.completedAt(),
                null,
                null,
                result.riskReasons()
        );
    }

    /** 잔액조회 결과. 조회는 돈을 움직이지 않으므로 세션을 이어서 쓸 수 있게 열어 둔다. */
    public static VoiceCommandResponse balance(
            final VoiceSession session,
            final BalanceResponse balance,
            final String transcript
    ) {
        return new VoiceCommandResponse(
                session.getId(),
                session.getStatus(),
                VoiceIntent.BALANCE,
                transcript,
                List.of(),
                null,
                null,
                null,
                null,
                session.getExpiresAt(),
                null,
                null,
                null,
                null,
                null,
                balance,
                List.of()
        );
    }

    /**
     * 위험 근거를 뒤에 덧붙인다.
     *
     * <p>화면을 보지 않는 사용자는 이 문장만 듣는다. "위험해서 막았어요"만으로는
     * 무엇을 고쳐 다시 시도해야 할지 알 수 없다.
     */
    private String withRiskReasons(final String message) {
        if (this.riskReasons == null || this.riskReasons.isEmpty()) {
            return message;
        }
        return message + " " + String.join(", ", this.riskReasons) + ".";
    }

    public String toVoiceMessage() {
        if (this.balance != null) {
            return this.balance.toVoiceMessage();
        }
        if (this.history != null) {
            return this.history.toVoiceMessage();
        }
        if (this.state == VoiceSessionStatus.CANCELED) {
            return CANCELED_MESSAGE;
        }
        if (this.status == TransferStatus.COMPLETED) {
            final String formattedAmount = KoreanMoneyFormatter.format(this.amount);
            return withRiskReasons("%s 님에게 %s을 보냈어요.".formatted(
                    this.recipientNameForResult(),
                    formattedAmount
            ));
        }
        if (this.status == TransferStatus.BLOCKED) {
            return withRiskReasons(ErrorCode.HIGH_RISK_BLOCKED.getVoiceMessage());
        }
        if (this.status == TransferStatus.FAILED) {
            return ErrorCode.TRANSFER_EXECUTION_FAILED.getVoiceMessage();
        }
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

    private String recipientNameForResult() {
        if (this.recipient != null) {
            return this.recipient.holderName();
        }
        return "받는 분";
    }

    /**
     * 거래내역 조회 결과.
     *
     * <p>{@code items}는 화면용 목록이고, 음성으로는 앞의 몇 건만 읽는다. 스무 건을 끝까지
     * 읽어 주면 듣는 사람이 따라올 수 없기 때문이다. 나머지는 건수로만 알린다.
     */
    public record History(
            String periodPhrase,
            String accountName,
            long totalCount,
            List<Item> items
    ) {

        /** 음성으로 읽어 줄 최대 건수 */
        private static final int SPOKEN_LIMIT = 3;

        private static final String EMPTY_MESSAGE = "%s %s 거래 내역이 없어요.";

        public History {
            if (items == null) {
                items = List.of();
            } else {
                items = List.copyOf(items);
            }
        }

        public static History of(
                final String periodPhrase,
                final String accountName,
                final long totalCount,
                final List<Item> items
        ) {
            return new History(periodPhrase, accountName, totalCount, items);
        }

        private String toVoiceMessage() {
            if (this.items.isEmpty()) {
                return EMPTY_MESSAGE.formatted(this.accountName, this.periodPhrase);
            }
            final StringBuilder message = new StringBuilder(
                    "%s %s 거래가 %d건 있어요.".formatted(
                            this.accountName,
                            this.periodPhrase,
                            this.totalCount
                    )
            );
            this.items.stream()
                    .limit(SPOKEN_LIMIT)
                    .forEach(item -> message.append(" ").append(item.toVoiceMessage()));
            final long remaining = this.totalCount - Math.min(this.items.size(), SPOKEN_LIMIT);
            if (remaining > 0L) {
                message.append(" 나머지 %d건은 화면에서 확인해 주세요.".formatted(remaining));
            }
            return message.toString();
        }
    }

    /** 거래내역 1건. 계좌번호와 잔액은 음성·화면 모두에 싣지 않는다. */
    public record Item(
            Long transactionId,
            TransactionType type,
            Long amount,
            String counterpartyName,
            LocalDateTime transactedAt
    ) {

        private static final String UNKNOWN_COUNTERPARTY = "이름 없는 거래";

        public static Item from(final TransactionResponse transaction) {
            return new Item(
                    transaction.transactionId(),
                    transaction.type(),
                    transaction.amount(),
                    transaction.counterpartyName(),
                    transaction.transactedAt()
            );
        }

        private String toVoiceMessage() {
            final String date = "%d월 %d일".formatted(
                    this.transactedAt.getMonthValue(),
                    this.transactedAt.getDayOfMonth()
            );
            final String formattedAmount = KoreanMoneyFormatter.format(this.amount);
            if (this.type == TransactionType.IN) {
                return "%s %s 님에게서 %s 받았어요.".formatted(
                        date,
                        this.counterpartyNameForVoice(),
                        formattedAmount
                );
            }
            return "%s %s 님에게 %s 보냈어요.".formatted(
                    date,
                    this.counterpartyNameForVoice(),
                    formattedAmount
            );
        }

        private String counterpartyNameForVoice() {
            if (this.counterpartyName == null || this.counterpartyName.isBlank()) {
                return UNKNOWN_COUNTERPARTY;
            }
            return this.counterpartyName;
        }
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

        public static Recipient named(final String holderName) {
            return new Recipient(null, holderName, null);
        }
    }
}
