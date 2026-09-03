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

    private VoiceCommandResponse confirmation(final String spokenAccountDigits) {
        return new VoiceCommandResponse(
                7L,
                VoiceSessionStatus.AWAITING_CONFIRMATION,
                VoiceIntent.TRANSFER,
                "농협 삼오이이삼일오칠사구로 만원 보내줘",
                List.of(),
                "confirm-1",
                new FromAccount(1L, "생활비 통장", "국민은행"),
                new Recipient(9L, "김주혁", "주혁", "011"),
                10_000L,
                null, null, null, null, null, null, null,
                List.of(),
                spokenAccountDigits
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
    @DisplayName("등록된 이름으로 보낼 때는 이름을 읽는다 - 계좌번호를 굳이 읽지 않는다")
    void 등록된_이름으로_보낼_때는_이름을_읽는다() {
        final String message = confirmation(null).toVoiceMessage();

        assertThat(message).isEqualTo("생활비 통장에서 주혁 님에게 1만원을 보낼까요?");
        assertThat(message).doesNotContain("계좌 ");
    }
}
