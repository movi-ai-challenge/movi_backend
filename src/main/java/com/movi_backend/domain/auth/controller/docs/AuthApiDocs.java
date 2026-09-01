package com.movi_backend.domain.auth.controller.docs;

import com.movi_backend.domain.auth.dto.request.PasswordLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.request.PinRegisterRequest;
import com.movi_backend.domain.auth.dto.request.SignUpRequest;
import com.movi_backend.domain.auth.dto.request.TokenRefreshRequest;
import com.movi_backend.global.security.JwtTokenPair;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * PIN 인증·토큰 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code AuthController}에 있다.
 */
@Tag(
        name = "인증",
        description = "일반 회원가입·로그인, PIN 로그인, JWT 토큰 관리."
)
public interface AuthApiDocs {

    @Operation(
            summary = "일반 회원가입",
            description = """
                    아이디와 비밀번호로 계정을 만듭니다. **카카오를 거치지 않는 유일한 가입 경로입니다.**

                    가입이 끝나면 곧바로 `accessToken`·`refreshToken`을 함께 내려 줍니다. 화면을 보지 않는
                    사용자에게 "가입됐으니 이제 로그인하세요"라는 두 번째 입력을 요구하지 않기 위해서입니다.
                    `newUser`는 항상 `true`입니다.

                    **아이디는 대소문자를 구분하지 않습니다.** `Movi`로 가입하면 `movi`로도 로그인됩니다.
                    영문·숫자·밑줄만 쓸 수 있고 4~30자입니다. 비밀번호는 8~64자이며 BCrypt로 저장합니다.

                    `phoneNumber`는 선택입니다. 넣으면 보호자 알림에 바로 쓸 수 있고, 생략하면 나중에
                    PIN 등록 단계에서 받습니다. 이미 다른 계정이 쓰는 번호는 거부합니다.
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "가입 성공. accessToken·refreshToken을 반환한다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` 아이디·비밀번호 형식 오류"),
            @ApiResponse(responseCode = "409", content = @Content,
                    description = "`AUTH_4092` 이미 사용 중인 아이디 · `AUTH_4091` 이미 다른 계정에 등록된 전화번호")
    })
    com.movi_backend.global.response.ApiResponse<LoginResponse> signUp(
            SignUpRequest request
    );

    @Operation(
            summary = "일반 로그인",
            description = """
                    아이디와 비밀번호로 로그인합니다.

                    **아이디가 없을 때도 비밀번호가 틀렸을 때와 같은 `AUTH_4024`를 반환합니다.** 응답이
                    갈리면 어떤 아이디가 가입돼 있는지 밖에서 확인할 수 있기 때문입니다.

                    **연속 5회 실패하면 5분간 잠깁니다.** 잠금은 계정 단위라 비밀번호를 5회 틀리면
                    PIN 로그인도 같이 막힙니다. 수단을 바꿔 가며 시도 횟수를 늘리지 못하게 하기 위해서입니다.

                    카카오로만 가입해 비밀번호가 없는 계정은 `AUTH_4026`으로 거부합니다.
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "로그인 성공. accessToken·refreshToken을 반환한다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`AUTH_4026` 비밀번호가 등록되지 않은 계정(카카오 전용)"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4024` 아이디 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`AUTH_4025` 연속 실패로 잠긴 계정")
    })
    com.movi_backend.global.response.ApiResponse<LoginResponse> login(
            PasswordLoginRequest request
    );

    @Operation(
            summary = "PIN 로그인",
            description = """
                    전화번호와 PIN으로 로그인합니다. 카카오로 가입한 뒤 PIN을 등록한 사용자가 쓰는 경로로,
                    **매번 카카오를 거치지 않고 바로 들어올 수 있게** 하는 수단입니다.

                    PIN은 BCrypt로 저장되어 있어 원문을 비교하지 않습니다.

                    **연속 실패하면 계정이 잠깁니다.** 잠금 중에는 PIN이 맞아도 검증하지 않고 즉시 거부합니다.
                    잠금 해제 시각까지 기다려야 합니다.
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "로그인 성공. accessToken·refreshToken을 반환한다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` 요청 형식 오류"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 전화번호 또는 PIN 불일치"),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`AUTH_4031` 연속 실패로 잠긴 계정"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`AUTH_4040` 등록되지 않은 사용자")
    })
    com.movi_backend.global.response.ApiResponse<LoginResponse> loginWithPin(
            PinLoginRequest request
    );

    @Operation(
            summary = "PIN 등록",
            description = """
                    카카오 로그인을 마친 사용자가 PIN을 처음 등록합니다. **이미 등록했다면 다시 등록할 수 없습니다** —
                    회원 한 명당 PIN은 하나입니다.

                    등록 후에는 `POST /api/v1/auth/pin/login`으로 로그인할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 완료"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` PIN 형식 오류 · `AUTH_4002` 이미 등록된 PIN"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 인증 필요")
    })
    com.movi_backend.global.response.ApiResponse<Void> registerPin(
            @Parameter(hidden = true) AuthUser authUser,
            PinRegisterRequest request
    );

    @Operation(
            summary = "토큰 갱신",
            description = """
                    Refresh 토큰으로 새 Access 토큰을 받습니다. Access 토큰이 만료됐을 때 다시 로그인시키지
                    않기 위한 경로입니다.

                    **로그아웃한 뒤에는 갱신되지 않습니다.** 로그아웃이 `token_version`을 올려 그 이전에
                    발급된 토큰을 모두 무효로 만들기 때문입니다.
                    """,
            security = {}
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "갱신 성공. 새 토큰 쌍을 반환한다"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4011` 만료·위조되었거나 로그아웃으로 무효화된 토큰")
    })
    com.movi_backend.global.response.ApiResponse<JwtTokenPair> refresh(
            TokenRefreshRequest request
    );

    @Operation(
            summary = "로그아웃",
            description = """
                    `token_version`을 올려 **이미 발급된 Access·Refresh 토큰을 즉시 무효화**합니다.
                    클라이언트가 토큰을 지우지 못한 채 앱이 종료돼도 서버 쪽에서 끊깁니다.

                    다시 쓰려면 로그인부터 해야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 완료"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 인증 필요")
    })
    com.movi_backend.global.response.ApiResponse<Void> logout(
            @Parameter(hidden = true) AuthUser authUser
    );
}
