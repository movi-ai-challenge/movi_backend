package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.util.KoreanAmountFormatter;
import java.time.LocalDateTime;

/**
 * 이체 결과.
 *
 * <p><b>계좌번호를 담지 않는다.</b> 응답이 그대로 TTS로 읽히는데, 화면을 보지 못하는 사용자가
 * 공공장소에서 자기 계좌번호를 스피커로 듣게 할 수 없다.
 */
public record TransferResponse(
        Long transferId,
        TransferStatus status,
        Long amount,
        String recipientName,
        RiskLevel riskLevel,
        LocalDateTime completedAt
) {

    private static final String COMPLETED_VOICE_FORMAT = "%s 님에게 %s을 보냈어요.";
    private static final String HOLD_VOICE_FORMAT =
            "안전을 위해 잠시 멈췄어요. 평소와 다른 송금으로 보여요. "
                    + "%s 님에게 %s, 정말 보내시겠어요? 보내시려면 네, 아니면 아니요라고 말씀해 주세요.";
    private static final String BLOCKED_VOICE = "안전을 위해 이체를 중단했어요.";
    private static final String NOT_COMPLETED_VOICE = "송금을 진행하지 못했어요.";

    public static TransferResponse from(final Transfer transfer, final RiskLevel riskLevel) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmount(),
                resolveRecipientName(transfer),
                riskLevel,
                transfer.getCompletedAt()
        );
    }

    /** 사용자가 답을 해야 넘어가는 상태인지 여부. 클라이언트가 확인 화면·음성을 띄우는 기준이다. */
    public boolean requiresConfirmation() {
        return status == TransferStatus.HOLD;
    }

    /**
     * 사용자에게 읽어 줄 문구.
     *
     * <p>금액은 숫자를 그대로 넘기지 않는다. TTS가 {@code 50000원}을 어떻게 읽을지 보장할 수 없다.
     *
     * <p>재질문 문구는 <b>템플릿으로 고정한다.</b> 물어볼 때마다 표현이 달라지면 화면을 보지 못하는
     * 사용자는 무엇을 묻는 건지 매번 새로 파악해야 한다.
     *
     * <p>차단된 거래에서는 "보호자에게 알렸다"고 말하지 않는다. 알림은 비동기로 나가므로 이 시점에
     * 발송 성공을 단정할 수 없다. 실제로 일어난 사실만 말한다.
     */
    public String toVoiceMessage() {
        if (status == TransferStatus.COMPLETED) {
            return COMPLETED_VOICE_FORMAT.formatted(
                    recipientName, KoreanAmountFormatter.toKoreanWon(amount));
        }
        if (status == TransferStatus.HOLD) {
            return HOLD_VOICE_FORMAT.formatted(
                    recipientName, KoreanAmountFormatter.toKoreanWon(amount));
        }
        if (status == TransferStatus.BLOCKED) {
            return BLOCKED_VOICE;
        }
        return NOT_COMPLETED_VOICE;
    }

    /** 저장된 수취인의 호출명을 우선한다. 사용자가 아는 이름이 "엄마"라면 그렇게 읽어 준다. */
    private static String resolveRecipientName(final Transfer transfer) {
        final TransferRecipient recipient = transfer.getRecipient();
        if (recipient == null) {
            return transfer.getToHolderName();
        }
        return recipient.getNickname();
    }
}
