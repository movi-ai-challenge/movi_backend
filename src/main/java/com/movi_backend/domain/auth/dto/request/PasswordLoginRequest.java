package com.movi_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 일반 로그인 요청(아이디 + 비밀번호).
 *
 * <p>{@code deviceUuid}는 선택이다. 보내면 그 기기가 신뢰 기기로 등록돼 이후 이체의 FDS
 * 위험도가 낮아진다. 보내지 않아도 로그인은 되지만 매번 처음 보는 기기로 평가된다.
 */
public record PasswordLoginRequest(
        @NotBlank
        @Size(max = 30)
        String loginId,

        @NotBlank
        @Size(max = 64)
        String password,

        @Size(max = 100)
        String deviceUuid,

        @Size(max = 100)
        String deviceModel,

        @Size(max = 50)
        String osVersion
) {
}
