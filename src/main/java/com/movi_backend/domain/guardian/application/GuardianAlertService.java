package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.config.GuardianProperties;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianPermissionScope;
import com.movi_backend.domain.notification.application.AsyncNotificationDispatcher;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.domain.notification.type.NotificationTemplate;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활성 보호자에게 알림을 보낸다.
 *
 * <p>보호자 승인이 MVP에서 빠지면서 <b>알림이 이상거래에 대한 유일한 대응 수단</b>이 됐다.
 * 그만큼 확실히 나가야 하지만, 동시에 알림 실패가 이미 확정된 이체 상태를 되돌려서도 안 된다.
 * 그래서 이 서비스는 대상만 추려 비동기 발송기에 넘기고 결과를 기다리지 않는다.
 *
 * <p><b>반드시 이체 상태가 커밋된 뒤에 호출한다.</b> 커밋 전에 부르면 다른 커넥션에서 아직 보이지
 * 않는 {@code transfer_id}를 참조하게 된다.
 *
 * <p>어떤 문구를 보낼지는 호출부가 정한다. 고위험 거래는 감지·진행·차단으로 상황이 갈리는데,
 * 그 판단은 이체 흐름을 쥐고 있는 쪽에 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardianAlertService {

    private final GuardianLinkRepository guardianLinkRepository;
    private final AsyncNotificationDispatcher asyncNotificationDispatcher;
    private final GuardianProperties guardianProperties;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 알림 수신 권한이 있는 활성 보호자 모두에게 보낸다.
     *
     * <p>보호자가 한 명도 없어도 조용히 지나간다. <b>보호자가 없다는 이유로 이체 판정을 바꾸지
     * 않는다.</b> 대상이 없었다는 사실만 로그로 남긴다.
     */
    @Transactional(readOnly = true)
    public void notifyActiveGuardians(
            final Long protecteeUserId,
            final Long transferId,
            final NotificationTemplate template
    ) {
        final List<GuardianLink> activeLinks = guardianLinkRepository
                .findAllByProtecteeUserIdAndStatus(protecteeUserId, GuardianLinkStatus.ACTIVE);
        final List<NotificationRequest> requests = activeLinks.stream()
                .filter(this::receivesAlert)
                .map(link -> toRequest(link, transferId, template))
                .toList();

        if (requests.isEmpty()) {
            log.info("알림 대상 보호자가 없습니다. transferId={} template={}",
                    transferId, template.getCode());
            return;
        }
        asyncNotificationDispatcher.dispatchAll(requests);
    }

    private NotificationRequest toRequest(
            final GuardianLink guardianLink,
            final Long transferId,
            final NotificationTemplate template
    ) {
        return NotificationRequest.guardianTransferAlert(
                guardianUserIdOrNull(guardianLink),
                guardianLink.getId(),
                transferId,
                template,
                sensitiveDataCrypto.decrypt(guardianLink.getGuardianPhone())
        );
    }

    /** 미가입 보호자는 회원 ID가 없다. 전화번호로만 보낸다. */
    private Long guardianUserIdOrNull(final GuardianLink guardianLink) {
        final User guardianUser = guardianLink.getGuardianUser();
        if (guardianUser == null) {
            return null;
        }
        return guardianUser.getId();
    }

    /**
     * 알림 수신 권한이 있는 보호자인지 판단한다.
     *
     * <p>{@code permission_scope}가 비어 있을 때의 기본값을 코드에서 정하지 않는다. 기능명세가
     * 확정하지 않은 값이라 설정({@code movi.guardian.default-receive-alert})으로 뺐다.
     * 팀 정책이 정해지면 설정만 바꾼다.
     */
    private boolean receivesAlert(final GuardianLink guardianLink) {
        return GuardianPermissionScope.readReceiveAlert(guardianLink.getPermissionScope())
                .orElseGet(guardianProperties::defaultReceiveAlert);
    }
}
