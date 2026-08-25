package com.movi_backend.domain.guardian.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 보호자 등록 요청.
 *
 * <p>{@code protecteeUserId}는 받지 않는다. 현재 사용자는 {@code @CurrentUser}로 식별한다.
 * 요청 본문으로 받으면 남의 계정에 보호자를 붙일 수 있다.
 *
 * <p>전화번호 형식 검증은 서버 정규화 이후에 한다. {@code 010-1234-5678}과
 * {@code +82 10 1234 5678}을 모두 받아야 하므로 정규식으로 미리 막지 않는다.
 */
public record GuardianLinkCreateRequest(
        @NotBlank
        @Size(max = 50)
        String guardianName,

        @NotBlank
        String guardianPhone,

        @NotBlank
        String relation
) {
}
