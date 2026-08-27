package com.movi_backend.domain.auth.application;

import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 기기 등록과 신뢰 승격.
 *
 * <p>FDS는 "익숙한 기기에서 온 이체인가"를 위험 신호로 쓴다. 계좌를 털린 사람의 이체는
 * 대개 처음 보는 기기에서 나가기 때문이다. 그 신호가 의미를 가지려면 기기를 실제로
 * 기록해야 한다.
 *
 * <p><b>신뢰 기준은 "PIN 인증을 통과한 적이 있는 기기"다.</b> PIN은 사용자만 아는 값이므로
 * 그 기기에서 본인이 확인됐다는 뜻이다. 카카오 로그인만으로는 승격하지 않는다 — 소셜 세션은
 * 기기 소유를 증명하지 않는다.
 *
 * <p>기기를 못 찾거나 남의 기기면 예외를 던지지 않고 <b>기기 없음</b>으로 처리한다.
 * 신뢰 정보가 없다는 것은 FDS에서 위험 쪽으로 기울 뿐이고, 로그인을 막을 이유는 아니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceRegistrationService {

    private final DeviceRepository deviceRepository;

    /**
     * PIN 인증에 성공한 기기를 등록하고 신뢰로 올린다.
     *
     * <p>{@code deviceUuid}가 없으면 아무것도 하지 않는다. 기기 식별자를 보내지 않는 클라이언트도
     * 로그인은 되어야 하고, 그 경우 이후 이체는 비신뢰 기기로 평가된다.
     */
    @Transactional
    public void registerTrusted(
            final User user,
            final String deviceUuid,
            final String deviceModel,
            final String osVersion
    ) {
        if (isBlank(deviceUuid)) {
            return;
        }
        final String normalizedUuid = deviceUuid.trim();
        final Optional<Device> existingDevice = deviceRepository.findByUserIdAndDeviceUuid(
                user.getId(),
                normalizedUuid
        );
        if (existingDevice.isPresent()) {
            trust(existingDevice.get());
            return;
        }
        if (deviceRepository.existsByDeviceUuid(normalizedUuid)) {
            // device_uuid 는 전역 UNIQUE 다. 다른 사용자에게 이미 묶인 식별자를 넘겨받아
            // 신뢰를 옮기면 남의 기기 이력으로 이체가 통과한다. 등록하지 않고 넘어간다.
            log.warn("다른 사용자에게 등록된 기기 식별자입니다: userId={}", user.getId());
            return;
        }
        final Device device = deviceRepository.save(Device.builder()
                .user(user)
                .deviceUuid(normalizedUuid)
                .deviceModel(deviceModel)
                .osVersion(osVersion)
                .build());
        trust(device);
    }

    /**
     * 이 사용자의 기기를 찾는다. 등록되지 않았거나 남의 기기면 {@code null}이다.
     *
     * <p>호출자는 {@code null}을 비신뢰로 다루면 된다.
     */
    @Transactional(readOnly = true)
    public Device findOwnedDevice(final Long userId, final String deviceUuid) {
        if (isBlank(deviceUuid)) {
            return null;
        }
        return deviceRepository.findByUserIdAndDeviceUuid(userId, deviceUuid.trim())
                .orElse(null);
    }

    private void trust(final Device device) {
        device.trust();
        device.recordLogin(LocalDateTime.now());
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
