package com.movi_backend.domain.notification.application;

import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.global.config.AsyncConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림을 호출자 트랜잭션 밖에서 발송한다.
 *
 * <p>고위험 이체는 {@code BLOCKED} 확정이 먼저다. 그 트랜잭션 안에서 SMS를 보내면 Provider가
 * 3초를 물고 있는 동안 금융 상태 확정이 지연되고, Provider 예외가 롤백을 유발할 수도 있다.
 *
 * <p><b>반드시 호출자 트랜잭션이 커밋된 뒤에 호출한다.</b> 커밋 전에 넘기면 다른 커넥션에서
 * 아직 보이지 않는 {@code transfer_id}를 참조해 외래키 제약에 걸린다.
 *
 * <p>{@link NotificationService}와 클래스를 분리한 이유는 같은 빈 안에서 {@code @Async} 메서드를
 * 직접 호출하면 프록시를 타지 않아 그냥 동기 실행되기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncNotificationDispatcher {

    private final NotificationService notificationService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @Transactional
    public void dispatch(final NotificationRequest request) {
        notificationService.sendOnce(request);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @Transactional
    public void dispatchAll(final List<NotificationRequest> requests) {
        for (final NotificationRequest request : requests) {
            notificationService.sendOnce(request);
        }
    }
}
