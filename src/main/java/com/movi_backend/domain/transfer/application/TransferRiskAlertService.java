package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.guardian.application.GuardianAlertService;
import com.movi_backend.domain.notification.application.AsyncNotificationDispatcher;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.domain.notification.type.NotificationTemplate;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이체 위험 상황을 보호자와 본인 양쪽에 알린다.
 *
 * <p>보호자만 알면 정작 당사자는 자기 계좌에서 무슨 일이 벌어지는지 모른다. 반대로 본인만 알면
 * 전화로 지시받는 중인 사용자에게는 아무 도움이 안 된다. <b>두 방향을 함께 보낸다.</b>
 *
 * <p>상황별로 문구가 다르다.
 *
 * <ul>
 *   <li>감지 — 아직 안 나갔고 본인 확인을 기다리는 중</li>
 *   <li>진행 — 본인이 확인해서 실제로 나감</li>
 *   <li>차단 — 본인이 거절했거나 확인 시간이 지남</li>
 * </ul>
 *
 * <p>보호자 입장에서 "막힌 건지 나간 건지"를 구분하지 못하면 알림이 무의미하다.
 *
 * <p><b>모든 메서드는 이체 상태가 커밋된 뒤에 호출한다.</b> 발송은 비동기라 실패해도 이체 상태를
 * 되돌리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferRiskAlertService {

    private final GuardianAlertService guardianAlertService;
    private final AsyncNotificationDispatcher asyncNotificationDispatcher;
    private final UserRepository userRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 고위험 감지. 보호자와 본인 모두에게 알리고 본인 확인을 기다린다.
     *
     * <p>이 시점에 오픈뱅킹은 호출되지 않았다. 돈은 아직 그대로다.
     */
    public void highRiskDetected(final Long userId, final Long transferId) {
        guardianAlertService.notifyActiveGuardians(
                userId, transferId, NotificationTemplate.HIGH_RISK_DETECTED_ALERT);
        notifyUser(userId, transferId, NotificationTemplate.HIGH_RISK_SELF_ALERT);
    }

    /** 본인이 재확인해 이체가 실제로 나갔음을 보호자에게 알린다. */
    public void highRiskConfirmed(final Long userId, final Long transferId) {
        guardianAlertService.notifyActiveGuardians(
                userId, transferId, NotificationTemplate.HIGH_RISK_CONFIRMED_ALERT);
    }

    /** 본인 거절·확인 시간 초과로 최종 차단됐음을 보호자에게 알린다. */
    public void transferBlocked(final Long userId, final Long transferId) {
        guardianAlertService.notifyActiveGuardians(
                userId, transferId, NotificationTemplate.BLOCKED_TRANSFER_ALERT);
    }

    /** 중위험 이체 사후 통보. 이체는 이미 진행됐다. */
    public void mediumRiskCompleted(final Long userId, final Long transferId) {
        guardianAlertService.notifyActiveGuardians(
                userId, transferId, NotificationTemplate.RISK_TRANSFER_ALERT);
    }

    /**
     * 본인에게 문자를 보낸다.
     *
     * <p>전화번호를 복호화하지 못하는 경우가 있다. 회원가입 경로에 따라 값이 비어 있거나 과거 키로
     * 암호화된 데이터일 수 있다. 그때는 문자를 포기하고 넘어간다. <b>본인 문자 발송 실패가
     * 이체 판정이나 보호자 알림을 막아서는 안 된다.</b> 앱 화면과 음성 안내는 이미 나갔다.
     */
    @Transactional(readOnly = true)
    public void notifyUser(
            final Long userId,
            final Long transferId,
            final NotificationTemplate template
    ) {
        final Optional<String> phoneNumber = findPhoneNumber(userId);
        if (phoneNumber.isEmpty()) {
            log.info("본인 알림 대상 전화번호가 없습니다. userId={} transferId={}", userId, transferId);
            return;
        }
        asyncNotificationDispatcher.dispatch(NotificationRequest.selfTransferAlert(
                userId, transferId, template, phoneNumber.get()));
    }

    private Optional<String> findPhoneNumber(final Long userId) {
        final Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().getPhone() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(PhoneNumberNormalizer.normalize(
                    sensitiveDataCrypto.decrypt(user.get().getPhone())));
        } catch (final RuntimeException exception) {
            log.warn("본인 전화번호를 사용할 수 없습니다. userId={} type={}",
                    userId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
