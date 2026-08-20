package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 같은 이체·같은 보호자에게 같은 종류의 알림이 이미 나갔는지 확인한다.
     *
     * <p>{@code notifications}에는 이 조합에 대한 UNIQUE 제약이 없다. 재시도나 중복 호출로
     * 보호자에게 같은 문자가 여러 번 가는 것을 서비스 레벨에서 막는다.
     */
    boolean existsByTransferIdAndGuardianLinkIdAndTemplateCode(
            Long transferId,
            Long guardianLinkId,
            String templateCode
    );

    /**
     * 본인에게 보내는 알림의 중복 여부.
     *
     * <p>본인 알림에는 {@code link_id}가 없어 보호자용 조회를 그대로 쓸 수 없다.
     * {@code link_id = NULL} 비교는 어떤 행과도 매칭되지 않기 때문이다.
     */
    boolean existsByTransferIdAndUserIdAndTemplateCode(
            Long transferId,
            Long userId,
            String templateCode
    );
}
