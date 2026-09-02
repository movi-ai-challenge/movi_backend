package com.movi_backend.domain.voice.application.model;

import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSlot;
import java.util.List;

/**
 * 재질문 중인 대화의 문맥.
 *
 * <p>백엔드가 "누구에게 보낼까요?"라고 되물은 상태라면, 이어지는 발화는 "김민수"처럼
 * 짧다. 그 말만 놓고 전체 의도를 다시 분석하면 이체라는 것을 잃어버린다. 무엇을
 * 물어봤는지 AI 에 함께 알려 해당 슬롯만 뽑게 한다.
 *
 * <p><b>슬롯은 백엔드가 소유한다.</b> 프런트가 들고 있다가 보내면 앞선 발화의 금액·
 * 수취인이 화면 쪽에서 바뀔 수 있다.
 */
public record VoiceStreamContext(VoiceIntent pendingIntent, List<VoiceSlot> expectedSlots) {

    public VoiceStreamContext {
        expectedSlots = expectedSlots == null ? List.of() : List.copyOf(expectedSlots);
    }

    public static VoiceStreamContext empty() {
        return new VoiceStreamContext(null, List.of());
    }

    /** AI 가 쿼리 파라미터로 받는 형식. 값이 없으면 빈 문자열이다. */
    public String expectedSlotsParameter() {
        if (expectedSlots.isEmpty()) {
            return "";
        }
        return String.join(",", expectedSlots.stream().map(Enum::name).toList());
    }

    public String pendingIntentParameter() {
        if (pendingIntent == null) {
            return "";
        }
        return pendingIntent.name();
    }
}
