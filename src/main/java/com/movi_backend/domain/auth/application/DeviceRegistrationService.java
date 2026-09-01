package com.movi_backend.domain.auth.application;

import com.movi_backend.domain.auth.application.event.TrustedDeviceRegistrationRequested;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    private final UserRepository userRepository;

    /**
     * PIN 인증에 성공한 기기를 등록하고 신뢰로 올린다.
     *
     * <p>{@code deviceUuid}가 없으면 아무것도 하지 않는다. 기기 식별자를 보내지 않는 클라이언트도
     * 로그인은 되어야 하고, 그 경우 이후 이체는 비신뢰 기기로 평가된다.
     *
     * <p><b>별도 트랜잭션으로 돈다.</b> 기기 등록은 로그인의 곁가지이므로 여기서 무슨 일이
     * 생기든 로그인을 실패시키면 안 된다. 같은 트랜잭션에 두면 제약 위반 한 번이 트랜잭션을
     * rollback-only로 만들어, 잡아도 로그인 커밋이 함께 무너진다.
     */
    /**
     * 인증 트랜잭션이 <b>커밋된 뒤</b> 기기를 등록한다.
     *
     * <p>커밋 전에 부르면 {@code devices}가 참조하는 {@code users} 행의 잠금을 기다리다
     * 교착에 빠진다. 자세한 사정은 {@link TrustedDeviceRegistrationRequested}에 적어 두었다.
     *
     * <p>{@code fallbackExecution}을 켜 둔 것은 트랜잭션 밖에서 발행된 경우에도 그대로
     * 처리하기 위해서다. 그때는 잠금이 없으므로 미룰 이유가 없다.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTrustedDeviceRegistrationRequested(
            final TrustedDeviceRegistrationRequested event
    ) {
        registerTrusted(
                event.userId(),
                event.deviceUuid(),
                event.deviceModel(),
                event.osVersion()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerTrusted(
            final Long userId,
            final String deviceUuid,
            final String deviceModel,
            final String osVersion
    ) {
        if (isBlank(deviceUuid)) {
            return;
        }
        final String normalizedUuid = deviceUuid.trim();
        final Optional<Device> existingDevice = deviceRepository.findByUserIdAndDeviceUuid(
                userId,
                normalizedUuid
        );
        if (existingDevice.isPresent()) {
            trust(existingDevice.get());
            return;
        }
        if (deviceRepository.existsByDeviceUuid(normalizedUuid)) {
            // device_uuid 는 전역 UNIQUE 다. 다른 사용자에게 이미 묶인 식별자를 넘겨받아
            // 신뢰를 옮기면 남의 기기 이력으로 이체가 통과한다. 등록하지 않고 넘어간다.
            log.warn("다른 사용자에게 등록된 기기 식별자입니다: userId={}", userId);
            return;
        }
        saveNewDevice(userId, normalizedUuid, deviceModel, osVersion);
    }

    /**
     * 위 존재 확인과 저장 사이에 같은 식별자가 먼저 들어올 수 있다. 로그인 버튼을 두 번
     * 누르면 같은 기기에서 두 요청이 동시에 올라온다.
     *
     * <p>이때 UNIQUE 제약 위반을 그대로 올리면 PIN 인증까지 끝난 로그인이 서버 오류로 끝난다.
     * 기기를 신뢰로 올리지 못하는 것은 비신뢰로 평가된다는 뜻일 뿐이므로 로그인을 통과시킨다.
     */
    private void saveNewDevice(
            final Long userId,
            final String deviceUuid,
            final String deviceModel,
            final String osVersion
    ) {
        try {
            final Device device = deviceRepository.saveAndFlush(Device.builder()
                    .user(userRepository.getReferenceById(userId))
                    .deviceUuid(deviceUuid)
                    .deviceModel(deviceModel)
                    .osVersion(osVersion)
                    .build());
            trust(device);
        } catch (final DataIntegrityViolationException exception) {
            log.warn("기기 등록이 동시 요청과 충돌했습니다: userId={}", userId);
        }
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
