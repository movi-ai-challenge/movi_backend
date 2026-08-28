package com.movi_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PIN 최초 등록 요청.
 *
 * <p>등록 시점도 본인 확인이 끝난 순간이므로, {@code deviceUuid}를 보내면 그 기기를
 * 신뢰 기기로 등록한다. 자세한 정책은 {@code DeviceRegistrationService} 참조.
 */
public record PinRegisterRequest(
        @NotBlank
        String phoneNumber,

        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        String pin,

        @Size(max = 100)
        String deviceUuid,

        @Size(max = 100)
        String deviceModel,

        @Size(max = 50)
        String osVersion
) {
}
