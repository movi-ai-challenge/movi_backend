package com.movi_backend.domain.auth.repository;

import com.movi_backend.domain.auth.entity.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByUserIdAndDeviceUuid(Long userId, String deviceUuid);

    boolean existsByDeviceUuid(String deviceUuid);
}
