package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬·테스트용 SMS 대역.
 *
 * <p><b>본문을 로그로 남긴다.</b> 이 문구는 보호자 폰에 그대로 뜨고, 앱에서는 TTS로도 읽힌다.
 * 대역이 조용히 삼켜 버리면 문구가 깨졌는지 금액 표기가 이상한지를 실제로 문자를 보내 보기
 * 전까지 알 수 없다 — 발송 여부만이 아니라 무엇이 나가는지가 검증 대상이다.
 *
 * <p>전화번호는 암호문 그대로 두고 로그에 남기지 않는다. 발송 문구에는 금액과 안내만 들어가고
 * 계좌번호·전화번호가 들어가지 않는다({@code GuardianNotificationTransactionService}의 템플릿).
 * 템플릿에 민감정보를 넣게 되면 이 로그부터 다시 검토해야 한다.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class MockSmsNotificationSender implements SmsNotificationSender {

    @Override
    public String send(
            final Long notificationId,
            final String encryptedTargetPhone,
            final String templateCode,
            final String message
    ) {
        log.info(
                "[MOCK SMS] 발송하지 않고 기록만 남깁니다. notificationId={} template={} message={}",
                notificationId,
                templateCode,
                message
        );
        return "mock-" + notificationId;
    }
}
