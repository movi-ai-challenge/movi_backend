package com.movi_backend.domain.notification.type;

import com.movi_backend.domain.guardian.type.NotificationChannel;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 발송 문구 템플릿.
 *
 * <p>문구를 코드에 흩어 두면 같은 상황에서 매번 다른 문장이 나간다. 보호자는 이 문자 하나로
 * 상황을 판단하므로 표현을 고정한다.
 *
 * <p><b>본문에 계좌번호·전화번호·PIN·토큰·모델 점수를 넣지 않는다.</b> 문자는 잠금 화면에
 * 그대로 노출되고 통신사 경로에도 남는다. 상세 내용은 앱에서 확인하도록 유도한다.
 *
 * <p>고위험 거래는 상황이 셋으로 갈린다. 감지·진행·차단을 한 문구로 뭉뚱그리면 보호자가
 * "막힌 건지 나간 건지"를 알 수 없다. 그래서 템플릿을 나눠 두었다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    /** 보호자 등록 통보. 확인 절차 없이 이미 연결이 끝난 뒤 보내는 안내다. */
    GUARDIAN_LINK_REGISTERED(
            NotificationChannel.SMS,
            "[Movi] {protecteeName} 님이 회원님을 보호자로 등록했습니다. "
                    + "앞으로 이상 거래가 감지되면 문자로 알려드립니다."
    ),

    /** 고위험 감지 → 보호자에게. 아직 이체는 나가지 않았고 본인 확인을 기다리는 중이다. */
    HIGH_RISK_DETECTED_ALERT(
            NotificationChannel.SMS,
            "[Movi] 보호 대상자의 고위험 이체 요청이 감지되어 본인 확인을 요청했습니다. "
                    + "앱에서 거래 내역을 확인해 주세요."
    ),

    /** 고위험 감지 → 본인에게. 본인이 요청한 게 아니라면 바로 알아채야 한다. */
    HIGH_RISK_SELF_ALERT(
            NotificationChannel.SMS,
            "[Movi] 평소와 다른 고위험 이체 요청이 감지되어 확인을 요청했습니다. "
                    + "본인이 요청한 것이 아니라면 송금을 취소하고 은행에 연락해 주세요."
    ),

    /** 본인 확인 후 진행됨 → 보호자에게. 막힌 게 아니라 나갔다는 사실을 알린다. */
    HIGH_RISK_CONFIRMED_ALERT(
            NotificationChannel.SMS,
            "[Movi] 고위험으로 감지된 이체를 보호 대상자가 본인 확인 후 진행했습니다. "
                    + "앱에서 거래 내역을 확인해 주세요."
    ),

    /** 최종 차단 통보 (10.3). 본인 거절 또는 확인 시간 초과. 최소 정보만 담는다. */
    BLOCKED_TRANSFER_ALERT(
            NotificationChannel.SMS,
            "[Movi] 보호 대상자의 고위험 이체 요청이 감지되어 거래를 차단했습니다. "
                    + "앱에서 거래 내역을 확인해 주세요."
    ),

    /** 중위험 거래 사후 통보. 이체는 이미 진행됐다. */
    RISK_TRANSFER_ALERT(
            NotificationChannel.SMS,
            "[Movi] 보호 대상자의 이체에서 평소와 다른 점이 확인되었습니다. "
                    + "앱에서 거래 내역을 확인해 주세요."
    );

    private static final String PLACEHOLDER_PREFIX = "{";
    private static final String PLACEHOLDER_SUFFIX = "}";

    private final NotificationChannel channel;
    private final String bodyTemplate;

    /** 템플릿 코드. {@code notifications.template_code}에 그대로 저장한다. */
    public String getCode() {
        return name();
    }

    /** {@code {key}} 자리를 치환해 실제 발송 문구를 만든다. */
    public String render(final Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return bodyTemplate;
        }
        String rendered = bodyTemplate;
        for (final Map.Entry<String, String> variable : variables.entrySet()) {
            final String placeholder = PLACEHOLDER_PREFIX + variable.getKey() + PLACEHOLDER_SUFFIX;
            rendered = rendered.replace(placeholder, variable.getValue());
        }
        return rendered;
    }
}
