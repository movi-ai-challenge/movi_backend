package com.movi_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일반 회원가입 요청. 카카오를 거치지 않고 계정을 만든다.
 *
 * <p>{@code loginId}는 개인정보가 아니라 사용자가 직접 정하는 식별자다. 대소문자를 구분하지
 * 않도록 서비스 계층에서 소문자로 정규화한 뒤 저장한다. 그래야 {@code Movi}로 가입한 사람이
 * {@code movi}로 로그인해도 같은 계정을 찾는다.
 *
 * <p>{@code phoneNumber}는 선택이다. 카카오 가입도 전화번호 없이 시작하므로 같은 기준을 둔다.
 * 다만 보호자 알림이 전화번호를 쓰므로, 넣어 두면 별도 등록 단계를 건너뛸 수 있다.
 */
public record SignUpRequest(
        @NotBlank
        @Size(min = 4, max = 30)
        @Pattern(
                regexp = "[a-zA-Z0-9_]+",
                message = "아이디는 영문, 숫자, 밑줄만 쓸 수 있습니다."
        )
        String loginId,

        /*
         * 최소 8자. 시각장애인·시니어가 화면 없이 입력하는 서비스라 특수문자 조합까지
         * 강제하지 않는다. 대신 5회 실패 시 5분 잠금으로 무차별 대입을 막는다.
         */
        @NotBlank
        @Size(min = 8, max = 64)
        String password,

        @NotBlank
        @Size(max = 50)
        String name,

        String phoneNumber,

        @Size(max = 100)
        String deviceUuid,

        @Size(max = 100)
        String deviceModel,

        @Size(max = 50)
        String osVersion
) {
}
