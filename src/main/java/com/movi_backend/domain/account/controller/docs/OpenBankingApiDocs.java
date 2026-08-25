package com.movi_backend.domain.account.controller.docs;

import com.movi_backend.domain.account.dto.response.ConnectResultResponse;
import com.movi_backend.domain.account.dto.response.ConnectStartResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 오픈뱅킹 계좌 연결 API 문서 (명세서 1.1, 1.2).
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code OpenBankingController}에 있다.
 */
@Tag(
        name = "오픈뱅킹 연결",
        description = "금융결제원 오픈뱅킹으로 사용자의 은행 계좌를 연결한다. 3-legged OAuth 흐름이다."
)
public interface OpenBankingApiDocs {

    @Operation(
            summary = "계좌 연결 시작",
            description = """
                    오픈뱅킹 인증 페이지 URL을 만들어 반환합니다. **프론트는 이 URL로 사용자를 보냅니다.**

                    사용자가 은행을 고르고 동의하면 오픈뱅킹이 `redirect-uri`로 인가 코드를 돌려주고,
                    그 요청을 `GET /api/openbanking/callback`이 받습니다.

                    URL에는 위조 방지용 `state`가 들어 있습니다. 백엔드가 만들어 보관했다가 콜백에서 대조합니다.
                    콜백은 인증되지 않은 요청으로 들어오므로 **`state`가 유일한 신원 증명**입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "인증 URL 생성. `data.authorizationUrl`로 이동시킨다")
    })
    com.movi_backend.global.response.ApiResponse<ConnectStartResponse> startConnect(
            @Parameter(hidden = true) AuthUser authUser
    );

    @Operation(
            summary = "계좌 연결 콜백",
            description = """
                    오픈뱅킹이 인가 코드를 돌려주는 지점입니다. **프론트가 직접 호출하는 API가 아니라
                    오픈뱅킹이 브라우저를 보내는 곳입니다.**

                    백엔드는 다음을 처리합니다.

                    1. `state`를 보관한 값과 대조하고 즉시 폐기 — 재사용을 막습니다
                    2. 인가 코드를 액세스 토큰으로 교환
                    3. 사용자의 계좌 목록을 받아 저장
                    4. 첫 연결이면 계좌 하나를 기본 계좌로 지정

                    **오픈뱅킹 토큰은 암호화해 저장합니다.** 복호화는 외부 API를 호출하기 직전에만 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연결 완료. 등록된 계좌 수를 반환한다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`OPENBANK_4002` state 불일치 또는 만료 · `SRV_4000` 인가 코드 누락"),
            @ApiResponse(responseCode = "502", content = @Content,
                    description = "`OPENBANK_5000` 오픈뱅킹 통신 실패")
    })
    com.movi_backend.global.response.ApiResponse<ConnectResultResponse> callback(
            @Parameter(description = "오픈뱅킹이 발급한 인가 코드") String code,
            @Parameter(description = "연결 시작 때 발급한 위조 방지 값") String state
    );
}
