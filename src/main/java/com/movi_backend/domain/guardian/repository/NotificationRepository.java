package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
