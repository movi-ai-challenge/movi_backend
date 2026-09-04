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
        List<String> riskReasons,

        /**
         * 확인 단계에서 읽어 줄 계좌번호. 자리마다 띄어 둔 문자열이다.
         *
         * <p>등록해 둔 이름으로 보낼 때는 {@code null} 이다. 계좌번호를 말해서 보내는
         * 경우에만 채운다 — 사용자가 잘못 들은 번호를 잡을 유일한 수단이라 뒤 네 자리가
         * 아니라 <b>전체</b>를 읽어 준다. TTS 가 "삼십오억..."으로 읽지 않도록 이미
         * 한 자리씩 끊어 둔 값이 온다.
         */
        String spokenAccountDigits,

        /**
         * 되물을 문장. 무엇이 빠졌는지에 따라 달라진다.
         *
         * <p>슬롯({@code missingSlots})만으로는 문장을 정할 수 없다. 은행을 못 들은 것과
         * 계좌번호를 못 들은 것은 둘 다 {@code RECIPIENT} 이지만 사용자가 다음에 말해야 할
         * 것이 다르다. 화면을 보지 않는 사용자에게 재질문 문구는 유일한 안내라, 검증이
         * 판단한 문장을 그대로 들려준다.
         *
         * <p>{@code null} 이면 슬롯에서 기본 문장을 고른다.
         */
        String clarifyingQuestion
) {

    private static final String RECIPIENT_QUESTION = "누구에게 보내시겠어요?";
    private static final String AMOUNT_QUESTION = "얼마를 보내시겠어요?";

    /**
     * 확인 질문 끝에 붙이는 답변 안내.
     *
     * <p>"네" 한 음절은 STT 가 잡지 못한다. 운영 기록을 보면 확인이 성공한 발화는 전부
     * "네 맞아요"·"네 보내 주세요" 처럼 두 어절 이상이었고, "네" 만 말한 시도는 한 번도
     * 통과하지 못했다(EMPTY_TRANSCRIPT 로 떨어졌다). 0.3초짜리 오디오로는 인식할 것이
     * 없기 때문이다.
     *
     * <p>화면을 보지 않는 사용자는 들은 대로 답한다. 그러니 들려주는 문장이 인식되는
     * 말이어야 한다 -- 무엇을 말해야 하는지 알려 주는 것이 인식률을 올리는 가장 확실한
     * 방법이다.
     */
    private static final String ANSWER_GUIDE = "맞으면 \"네 맞아요\", 아니면 \"아니요 취소할게요\"라고 말씀해 주세요.";
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
        return clarifying(session, missingSlots, transcript, null);
    }

    /** 무엇이 빠졌는지에 맞춘 문장을 함께 실어 보낸다. */
    public static VoiceCommandResponse clarifying(
            final VoiceSession session,
            final List<TransferSlot> missingSlots,
            final String transcript,
            final String clarifyingQuestion
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
                List.of(),
                null,
                clarifyingQuestion
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
        return awaitingConfirmation(
                session, confirmationId, account, transferRecipient, amount, transcript, null);
    }

    /**
     * 계좌번호를 말해서 보내는 경우까지 포함해 만든다.
     *
     * <p>{@code spokenAccountDigits} 가 있으면 확인 문구가 계좌번호를 자리마다 읽어 준다.
     */
    public static VoiceCommandResponse awaitingConfirmation(
            final VoiceSession session,
            final String confirmationId,
            final Account account,
            final TransferRecipient transferRecipient,
            final long amount,
            final String transcript,
            final String spokenAccountDigits
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
                List.of(),
                spokenAccountDigits,
                null
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
                List.of(),
                null,
                null
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
                List.of(),
                null,
                null
        );
    }

    public static VoiceCommandResponse executed(final TransferExecutionResult result) {
        return executed(result, null);
    }

    public static VoiceCommandResponse executed(
            final TransferExecutionResult result,
            final String transcript
    ) {
        VoiceSessionStatus responseState = VoiceSessionStatus.COMPLETED;
        if (result.status() == TransferStatus.PENDING
                || result.status() == TransferStatus.RISK_REVIEW) {
            responseState = VoiceSessionStatus.PROCESSING;
        }
        return new VoiceCommandResponse(
                null,
                responseState,
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
                result.riskReasons(),
                null,
                null
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
                List.of(),
                null,
                null
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
        if (this.status == TransferStatus.PENDING
                || this.status == TransferStatus.RISK_REVIEW) {
            return "은행의 송금 결과를 확인하고 있어요. 다시 송금하지 마세요.";
        }
        if (this.state == VoiceSessionStatus.CLARIFYING) {
            /*
             * 검증이 정한 문장이 있으면 그것을 쓴다. 슬롯만 보면 "은행을 못 들었다"와
             * "계좌번호를 못 들었다"가 같은 RECIPIENT 로 뭉뚱그려져, 사용자가 다음에 무엇을
             * 말해야 하는지 알 수 없다.
             */
            if (this.clarifyingQuestion != null && !this.clarifyingQuestion.isBlank()) {
                return this.clarifyingQuestion;
            }
            if (this.missingSlots.contains(TransferSlot.RECIPIENT)) {
                return RECIPIENT_QUESTION;
            }
            return AMOUNT_QUESTION;
        }
        final String accountName = this.fromAccount.toVoiceName();
        final String formattedAmount = KoreanMoneyFormatter.format(this.amount);
        if (this.spokenAccountDigits != null && !this.spokenAccountDigits.isBlank()) {
            /*
             * 계좌번호를 말해서 보내는 경우다. 등록해 둔 상대가 아니라 사용자가 방금 부른
             * 번호로 나가므로, 뒤 네 자리만 읽으면 가운데를 잘못 들은 것을 잡지 못한다.
             * 화면을 볼 수 없는 사용자에게는 이 복창이 유일한 확인 수단이다.
             */
            return "%s에서 %s 계좌 %s으로 %s을 보낼까요? %s".formatted(
                    accountName,
                    this.recipient.voiceName(),
                    this.spokenAccountDigits,
                    formattedAmount,
                    ANSWER_GUIDE
            );
        }
        return "%s에서 %s 님에게 %s을 보낼까요? %s".formatted(
                accountName,
                this.recipient.voiceName(),
                formattedAmount,
                ANSWER_GUIDE
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
    public record Recipient(Long recipientId, String holderName, String nickname, String bankCode) {

        public static Recipient from(final TransferRecipient recipient) {
            return new Recipient(
                    recipient.getId(),
                    recipient.getHolderName(),
                    recipient.getNickname(),
                    recipient.getBankCode()
            );
        }

        public static Recipient named(final String holderName) {
            return new Recipient(null, holderName, null, null);
        }

        private String voiceName() {
            if (this.nickname != null && !this.nickname.isBlank()) {
                return this.nickname;
            }
            return this.holderName;
        }
    }
}
