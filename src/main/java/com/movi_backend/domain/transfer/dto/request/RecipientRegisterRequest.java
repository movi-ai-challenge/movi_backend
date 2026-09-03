package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 주소록에 상대방을 등록하는 요청.
 *
 * <p><b>은행을 함께 받는다.</b> 계좌번호는 은행 안에서만 유일해서, 은행을 모르면 어느
 * 계좌인지 정해지지 않는다. 예전에는 계좌 접두어로 은행을 추정했는데 앞자리가 같은 다른
 * 은행 계좌가 걸렸다. 추정하지 않고 사용자가 고른 은행을 쓴다.
 *
 * <p>예금주는 받지 않는다. 사람이 옮겨 적으면 틀리고, 틀린 예금주가 확인 복창에서 읽히면
 * 사용자는 맞는 사람에게 보내는 것으로 듣는다. 예금주는 <b>예금주조회로 확인한 값</b>만 쓴다.
 *
 * @param name          음성으로 부를 이름. "엄마", "김민수" 처럼 사용자가 실제로 말할 말이다
 * @param bankCode      은행 코드. 프런트가 은행 목록에서 고른 값을 그대로 보낸다
 * @param accountNumber 상대방 계좌번호. 하이픈·공백이 섞여 있어도 된다
 */
public record RecipientRegisterRequest(

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이내로 입력해 주세요.")
        String name,

        @NotBlank(message = "은행을 선택해 주세요.")
        @Pattern(regexp = "[0-9]{3}", message = "은행 코드는 숫자 세 자리입니다.")
        String bankCode,

        /*
         * 하이픈·공백을 허용하되 숫자가 몇 개인지는 여기서 세지 않는다. "------" 처럼 형식만
         * 맞고 숫자가 없는 입력이 통과하므로, 숫자 개수는 TransferTargetVerifier 가 확인한다.
         * 한 곳에서 세야 음성 경로와 화면 경로의 판정이 갈리지 않는다.
         */
        @NotBlank(message = "계좌번호를 입력해 주세요.")
        @Pattern(
                regexp = "[0-9\\-\\s]{6,30}",
                message = "계좌번호는 숫자와 하이픈으로 입력해 주세요."
        )
        String accountNumber
) {
}
