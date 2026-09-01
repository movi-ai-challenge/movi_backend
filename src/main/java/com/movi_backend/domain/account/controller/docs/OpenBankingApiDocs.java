package com.movi_backend.domain.account.controller.docs;

import com.movi_backend.domain.account.dto.response.ConnectResultResponse;
import com.movi_backend.domain.account.dto.response.ConnectStartResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
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
                    오픈뱅킹이 브라우저를 보내는 곳입니다.** 인증이 걸려 있지 않습니다 — 사용자의
                    토큰을 들고 올 수 없는 자리이며, `state` 대조가 유일한 신원 증명입니다.

                    백엔드는 다음을 처리합니다.

                    1. `state`를 보관한 값과 대조하고 즉시 폐기 — 재사용을 막습니다
                    2. 인가 코드를 액세스 토큰으로 교환
                    3. 사용자의 계좌 목록을 받아 저장
                    4. 첫 연결이면 계좌 하나를 기본 계좌로 지정

                    **응답은 JSON이 아니라 프론트 화면으로 가는 302입니다.** 사용자의 브라우저가
                    은행에서 돌아오는 지점이라 본문을 반환하면 사용자가 JSON을 마주하고 흐름이
                    거기서 끊깁니다. 화면을 보지 않는 사용자에게는 무엇이 잘못됐는지 알 방법조차
                    없습니다.

                    성공·취소·실패를 **모두 같은 프론트 주소로** 돌려보내고 결과만 질의 문자열로
                    구분합니다.

                    | 상황 | 돌아가는 주소 |
                    |---|---|
                    | 연결 완료 | `{프론트 주소}?result=success` |
                    | 사용자 취소·은행 오류 | `{프론트 주소}?result=error&error=...` |
                    | state 불일치·통신 실패 | `{프론트 주소}?result=error&error=OPENBANK_4002` 등 |

                    `error`에는 **에러 코드만** 싣습니다. 이 주소는 브라우저 기록과 `Referer`에
                    남으므로 계좌번호나 토큰을 싣지 않습니다.

                    돌아갈 주소는 `movi.openbanking.frontend-redirect-uri`로 설정합니다.

                    **오픈뱅킹 토큰은 암호화해 저장합니다.** 복호화는 외부 API를 호출하기 직전에만 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", content = @Content,
                    description = "성공·실패 모두 프론트 콜백 화면으로 이동한다")
    })
    ResponseEntity<Void> callback(
            @Parameter(description = "오픈뱅킹 인가 코드") String code,
            @Parameter(description = "연결 시작 때 발급한 1회용 state") String state,
            @Parameter(description = "은행이 실패를 알릴 때 싣는 값") String error
    );

}
