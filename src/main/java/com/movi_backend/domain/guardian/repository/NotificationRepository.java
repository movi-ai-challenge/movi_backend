package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification
            from Notification notification
            join fetch notification.transfer
            where notification.status = :status
              and notification.nextRetryAt <= :now
            order by notification.nextRetryAt asc
            """)
    List<Notification> findDueRetries(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 내가 관련된 알림.
     *
     * <p>두 방향을 함께 본다 — <b>내 이체 때문에 나간 알림</b>(피보호자 관점)과 <b>내가 보호자로
     * 받은 알림</b>(보호자 관점)이다. 수신자({@code notification.user})만으로 거르면 미가입
     * 보호자에게 간 알림이 통째로 빠진다. 그 값은 초대 수락 전까지 null 이기 때문이다 —
     * 정작 발송을 확인해야 할 시연·초기 연동 구간이 전부 여기에 해당한다.
     *
     * <p>{@code guardianLink}를 함께 읽어 온다. 목록에서 보호자 이름·번호를 쓰는데 지연 로딩으로
     * 두면 건수만큼 추가 조회가 나간다.
     */
    @Query(
            value = """
                    select notification
                    from Notification notification
                    join fetch notification.guardianLink guardianLink
                    where guardianLink.protecteeUser.id = :userId
                       or notification.user.id = :userId
                    """,
            countQuery = """
                    select count(notification)
                    from Notification notification
                    where notification.guardianLink.protecteeUser.id = :userId
                       or notification.user.id = :userId
                    """
    )
    Page<Notification> findMine(@Param("userId") Long userId, Pageable pageable);
}
