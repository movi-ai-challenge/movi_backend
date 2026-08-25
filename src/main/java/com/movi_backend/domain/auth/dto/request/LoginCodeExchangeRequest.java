package com.movi_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 로그인 교환 코드.
 *
 * <p>리다이렉트 URL 로 받은 일회성 코드를 본문에 담아 보낸다. 쿼리스트링이 아니라 본문인
 * 이유는, 이 요청 자체가 로그 남는 곳을 지나가지 않게 하기 위해서다.
 */
public record LoginCodeExchangeRequest(
        @NotBlank
        String code
) {
}
