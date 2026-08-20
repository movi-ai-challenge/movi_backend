package com.movi_backend.domain.notification.infrastructure;

import com.movi_backend.domain.notification.dto.SmsMessage;
import com.movi_backend.domain.notification.dto.SmsSendResult;

/**
 * SMS 발송 게이트웨이.
 *
 * <p>국내 발신은 사업자 등록·발신번호 사전등록이 필요해 계약 전까지 실제 발송을 할 수 없다.
 * 그래서 인터페이스를 먼저 고정하고 {@link MockSmsProvider}로 개발한다.
 * 실제 Provider(NHN Toast·알리고 등)가 확정되면 구현체만 추가한다.
 *
 * <p>구현체는 <b>예외를 던지는 대신 실패 결과를 반환</b>하는 것을 권장한다. 알림 실패가
 * 이미 확정된 이체 차단 상태를 되돌려서는 안 되기 때문이다.
 */
public interface SmsProvider {

    SmsSendResult send(SmsMessage message);
}
