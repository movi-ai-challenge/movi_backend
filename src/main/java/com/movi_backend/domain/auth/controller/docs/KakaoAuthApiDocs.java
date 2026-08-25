package com.movi_backend.domain.auth.controller.docs;

import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 카카오 OAuth 로그인 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code KakaoAuthController}에 있다.
 */
@Tag(
        name = "카카오 로그인",
        description = "최초 계정 연결 경로. 가입과 로그인을 겸한다."
)
public interface KakaoAuthApiDocs {

    @Operation(
            summary = "카카오 인증 시작",
            description = """
                    카카오 인증 페이지로 **302 리다이렉트**합니다. JSON을 반환하지 않으므로 프론트는
                    이 주소로 브라우저를 이동시키기만 하면 됩니다.

                    응답에 위조 방지용 `state`가 `KAKAO_OAUTH_STATE` 쿠키로 함께 내려갑니다.
                    HttpOnly·SameSite=Lax·5분 만료이며, 콜백에서 쿼리의 `state`와 대조합니다.
                    **쿠키가 없으면 콜백이 실패합니다.**
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "302", content = @Content,
                    description = "카카오 인증 페이지로 이동. `Location` 헤더와 state 쿠키를 내려준다")
    })
    ResponseEntity<Void> authorize();

    @Operation(
            summary = "카카오 인증 콜백",
            description = """
                    카카오가 인가 코드를 돌려주는 지점입니다. **프론트가 직접 호출하는 API가 아닙니다.**

                    백엔드는 다음을 처리합니다.

                    1. 쿼리 `state`와 쿠키 `state`를 대조 — 둘 중 하나라도 없거나 다르면 거부합니다
                    2. 인가 코드로 카카오 토큰을 받고 사용자 정보를 조회
                    3. 처음 보는 카카오 계정이면 회원을 생성, 아니면 기존 회원과 연결
                    4. 서비스 자체 Access·Refresh JWT를 발급
                    5. state 쿠키를 즉시 만료

                    **전화번호는 암호화해 저장합니다.** 검색이 필요해 HMAC 해시를 따로 둡니다.

                    로그인 후 `POST /api/v1/auth/pin/register`로 PIN을 등록하면 다음부터는 카카오 없이
                    로그인할 수 있습니다.
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "로그인 성공. 토큰과 PIN 등록 여부를 반환한다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", content = @Content,
                    description = "`AUTH_4003` state 불일치·누락 · `SRV_4000` 인가 코드 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502", content = @Content,
                    description = "`AUTH_5000` 카카오 통신 실패")
    })
    ResponseEntity<ApiResponse<LoginResponse>> callback(
            @Parameter(description = "카카오가 발급한 인가 코드") String code,
            @Parameter(description = "인증 시작 때 발급한 위조 방지 값") String state,
            @Parameter(hidden = true) String stateCookie
    );
}
