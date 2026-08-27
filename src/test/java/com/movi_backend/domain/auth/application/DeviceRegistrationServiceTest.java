package com.movi_backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationServiceTest {

    private static final Long USER_ID = 3L;
    private static final String DEVICE_UUID = "device-uuid-1";

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceRegistrationService deviceRegistrationService;

    @Test
    @DisplayName("처음 보는 기기는 등록하면서 바로 신뢰 기기가 된다")
    void 처음_보는_기기는_등록하면서_바로_신뢰_기기가_된다() {
        // given
        final User user = createUser();
        given(deviceRepository.findByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
                .willReturn(Optional.empty());
        given(deviceRepository.existsByDeviceUuid(DEVICE_UUID)).willReturn(false);
        given(userRepository.getReferenceById(USER_ID)).willReturn(createUser());
        given(deviceRepository.saveAndFlush(any(Device.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        deviceRegistrationService.registerTrusted(USER_ID, DEVICE_UUID, "Galaxy S24", "Android 14");

        // then
        final ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        then(deviceRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDeviceUuid()).isEqualTo(DEVICE_UUID);
        assertThat(captor.getValue().isTrusted()).isTrue();
        assertThat(captor.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 등록된 기기는 다시 만들지 않고 신뢰 상태만 갱신한다")
    void 이미_등록된_기기는_다시_만들지_않고_신뢰_상태만_갱신한다() {
        // given
        final Device device = createDevice(createUser());
        given(deviceRepository.findByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
                .willReturn(Optional.of(device));

        // when
        deviceRegistrationService.registerTrusted(USER_ID, DEVICE_UUID, null, null);

        // then
        assertThat(device.isTrusted()).isTrue();
        then(deviceRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 사용자에게 등록된 기기 식별자는 신뢰를 옮기지 않는다")
    void 다른_사용자에게_등록된_기기_식별자는_신뢰를_옮기지_않는다() {
        // given — device_uuid 는 전역 UNIQUE 라 남의 식별자가 넘어올 수 있다
        given(deviceRepository.findByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
                .willReturn(Optional.empty());
        given(deviceRepository.existsByDeviceUuid(DEVICE_UUID)).willReturn(true);

        // when
        deviceRegistrationService.registerTrusted(USER_ID, DEVICE_UUID, null, null);

        // then
        then(deviceRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기기 식별자를 보내지 않아도 로그인은 막지 않는다")
    void 기기_식별자를_보내지_않아도_로그인은_막지_않는다() {
        // when
        deviceRegistrationService.registerTrusted(USER_ID, null, null, null);
        deviceRegistrationService.registerTrusted(USER_ID, "  ", null, null);

        // then
        then(deviceRepository).shouldHaveNoInteractions();
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("등록되지 않은 기기를 조회하면 신뢰 정보 없이 진행한다")
    void 등록되지_않은_기기를_조회하면_신뢰_정보_없이_진행한다() {
        // given
        given(deviceRepository.findByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
                .willReturn(Optional.empty());

        // when
        final Device found = deviceRegistrationService.findOwnedDevice(USER_ID, DEVICE_UUID);

        // then
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("기기 식별자가 없으면 조회하지 않는다")
    void 기기_식별자가_없으면_조회하지_않는다() {
        // when
        final Device found = deviceRegistrationService.findOwnedDevice(USER_ID, null);

        // then
        assertThat(found).isNull();
        then(deviceRepository).shouldHaveNoInteractions();
    }

    private User createUser() {
        final User user = User.builder()
                .name("김철수")
                .phone("encrypted")
                .userType(UserType.SENIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Device createDevice(final User user) {
        return Device.builder()
                .user(user)
                .deviceUuid(DEVICE_UUID)
                .build();
    }

    @Test
    @DisplayName("동시 요청이 같은 기기를 먼저 등록해도 로그인을 실패시키지 않는다")
    void 동시_요청이_같은_기기를_먼저_등록해도_로그인을_실패시키지_않는다() {
        // given — 존재 확인과 저장 사이에 같은 식별자가 들어온 상황
        given(deviceRepository.findByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
                .willReturn(Optional.empty());
        given(deviceRepository.existsByDeviceUuid(DEVICE_UUID)).willReturn(false);
        given(userRepository.getReferenceById(USER_ID)).willReturn(createUser());
        willThrow(new DataIntegrityViolationException("uk_device_uuid"))
                .given(deviceRepository).saveAndFlush(any(Device.class));

        // when & then — PIN 인증까지 끝난 로그인이 서버 오류로 끝나면 안 된다
        assertThatCode(() -> deviceRegistrationService.registerTrusted(
                USER_ID, DEVICE_UUID, null, null
        )).doesNotThrowAnyException();
    }
}
