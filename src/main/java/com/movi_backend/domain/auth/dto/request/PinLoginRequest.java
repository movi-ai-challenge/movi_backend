package com.movi_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinLoginRequest(
        @NotBlank String phoneNumber,
        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        String pin
) {
}
