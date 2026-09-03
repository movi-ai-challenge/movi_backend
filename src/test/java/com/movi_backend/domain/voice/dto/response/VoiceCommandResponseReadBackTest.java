package com.movi_backend.domain.voice.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse.FromAccount;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse.Recipient;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 확인 문구 복창 검증.
 *
 * <p>계좌번호를 말해서 보내는 경우, 이 문구가 <b>사용자가 잘못 들은 번호를 잡을 유일한
 * 수단</b>이다. 뒤 네 자리만 읽으면 가운데 한 자리가 틀려도 그대로 나간다.
 */
class VoiceCommandResponseReadBackTest {

    /** 확인 질문 끝에 붙는 답변 안내. 본문과 같은 값을 쓴다. */
    private static final String ANSWER_GUIDE =
            " 맞으면 \"네 맞아요\", 아니면 \"아니요 취소할게요\"라고 말씀해 주세요.";

    private VoiceCommandResponse confirmation(final String spokenAccountDigits) {
        return new VoiceCommandResponse(
                7L,
                VoiceSessionStatus.AWAITING_CONFIRMATION,
                VoiceIntent.TRANSFER,
                "농협 삼오이이삼일오칠사구로 만원 보내줘",
                List.of(),
                "confirm-1",
                new FromAccount(1L, "생활비 통장", "국민은행"),
                new Recipient(9L, "김주혁", "주혁", "011", "농협은행", "5749"),
                10_000L,
                null, null, null, null, null, null, null,
                List.of(),
                spokenAccountDigits,
                null
        );
    }

    @Test
    @DisplayName("계좌번호로 보낼 때는 전체 자릿수를 하나씩 읽어 준다")
    void 계좌번호로_보낼_때는_전체_자릿수를_읽어_준다() {
        final String message = confirmation("3 5 2 2 3 1 5 7 4 9").toVoiceMessage();

        assertThat(message).contains("3 5 2 2 3 1 5 7 4 9");
        assertThat(message).contains("1만원");
    }

    @Test
    @DisplayName("등록된 이름으로 보낼 때는 이름과 함께 확인된 예금주·은행·끝자리를 읽는다")
    void 등록된_이름으로_보낼_때도_확인된_예금주를_읽는다() {
        final String message = confirmation(null).toVoiceMessage();

        /*
         * 이름만 읽으면 같은 이름으로 저장한 다른 계좌를 구분할 수 없다. 사용자가 부른
         * 이름으로 시작하되, 확인된 예금주와 은행·끝자리를 붙여 무엇이 나가는지 짚어 준다.
         */
        assertThat(message).isEqualTo(
                "생활비 통장에서 주혁, 농협은행 김주혁 님, 끝자리 5749번 계좌로 1만원을 보낼까요?"
                        + ANSWER_GUIDE);
    }

    @Test
    @DisplayName("계좌번호로 보낼 때도 확인된 예금주와 은행을 함께 읽는다")
    void 계좌번호로_보낼_때도_확인된_예금주를_읽는다() {
        final String message = confirmation("3 5 2 2 3 1 5 7 4 9").toVoiceMessage();

        assertThat(message).contains("농협은행 김주혁 님");
    }
}
