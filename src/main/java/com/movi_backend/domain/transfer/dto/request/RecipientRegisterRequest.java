package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 상대방 등록 요청.
 *
 * <p>이름과 계좌번호만 받는다. 은행과 예금주는 사용자가 적는 값이 아니라 <b>연결된 계좌에서
 * 찾아 채운다</b> — 사람이 옮겨 적으면 틀리고, 틀린 은행으로 저장되면 음성 송금이 엉뚱한
 * 곳으로 향한다.
 *
 * @param name          음성으로 부를 이름. "엄마", "김민수" 처럼 사용자가 실제로 말할 말이다
 * @param accountNumber 상대방 계좌번호. 하이픈·공백이 섞여 있어도 된다
 */
public record RecipientRegisterRequest(

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이내로 입력해 주세요.")
        String name,

        @NotBlank(message = "계좌번호를 입력해 주세요.")
        @Pattern(
                regexp = "[0-9\\-\\s]{6,30}",
                message = "계좌번호는 숫자와 하이픈으로 6자 이상 입력해 주세요."
        )
        String accountNumber
) {
}
