package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 직접 입력 송금 검토 요청.
 *
 * <p>수취인을 <b>두 가지 방법</b>으로 지정한다. 둘 중 하나만 채운다.
 *
 * <ul>
 *   <li>{@code recipientId} — 주소록에 등록해 둔 상대</li>
 *   <li>{@code bankCode} + {@code accountNumber} — 등록하지 않은 상대에게 한 번 보낼 때</li>
 * </ul>
 *
 * <p>등록하지 않은 계좌를 받는다고 검증이 느슨해지지 않는다. 음성 경로와 <b>같은
 * 예금주조회</b>를 거치고, 확인되지 않으면 검토 자체가 실패한다. 키보드와 스크린리더만
 * 쓰는 사용자도 음성과 같은 일을 할 수 있어야 해서 두 경로를 같은 규칙으로 둔다.
 *
 * <p>예금주는 받지 않는다. 사용자가 적은 이름을 확인 문장에 그대로 쓰면 틀린 이름을 읽어
 * 주게 된다. 확인 문장의 예금주는 조회로 확인된 값만 쓴다.
 *
 * <p>{@code fromAccountId}를 비우면 기본 계좌에서 나간다.
 */
public record TransferReviewRequest(
        Long recipientId,

        @Pattern(regexp = "[0-9]{3}", message = "은행 코드는 숫자 세 자리입니다.")
        String bankCode,

        @Pattern(
                regexp = "[0-9\\-\\s]{6,30}",
                message = "계좌번호는 숫자와 하이픈으로 입력해 주세요."
        )
        String accountNumber,

        @NotNull
        @Positive
        Long amount,

        Long fromAccountId
) {

    /** 등록하지 않은 계좌로 보내는 요청인지. 은행과 계좌번호가 모두 있어야 한다. */
    public boolean hasOneTimeAccount() {
        return bankCode != null && !bankCode.isBlank()
                && accountNumber != null && !accountNumber.isBlank();
    }

    public boolean hasRegisteredRecipient() {
        return recipientId != null;
    }
}
