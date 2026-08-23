package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
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
}
