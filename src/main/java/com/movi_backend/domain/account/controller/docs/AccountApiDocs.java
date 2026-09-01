package com.movi_backend.domain.account.controller.docs;

import com.movi_backend.domain.account.dto.request.AccountAliasChangeRequest;
import com.movi_backend.domain.account.dto.response.AccountListResponse;
import com.movi_backend.domain.account.dto.response.AccountResponse;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 계좌·잔액 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code AccountController}에 있고, 컨트롤러 본문은
 * 어노테이션 없이 읽을 수 있도록 둔다.
 *
 * <p>여기서 {@code ApiResponse}는 Swagger 어노테이션이다. 서비스 공통 응답 타입과 이름이
 * 겹치므로 반환 타입 쪽을 정규화했다.
 */
@Tag(name = "계좌", description = "연결된 계좌 조회와 잔액조회. 계좌 연결 자체는 오픈뱅킹 API를 쓴다.")
public interface AccountApiDocs {

    @Operation(
            summary = "잔액조회",
            description = """
                    계좌 잔액을 오픈뱅킹에서 **실시간으로 다시 조회**해 반환합니다. 캐시를 읽지 않습니다.

                    `accountAlias`를 생략하면 기본 계좌를, 지정하면 그 별칭의 계좌를 조회합니다.
                    별칭은 사용자가 계좌에 붙인 이름("생활비 통장")입니다.

                    조회 결과는 `balance_snapshots`에 남습니다. FDS가 "잔액 대비 이체 비율"을 피처로 쓰고,
                    반복 호출 비용을 줄이기 위해서입니다.

                    `voiceMessage`에 한국어로 변환한 금액이 들어갑니다 — "국민은행 생활비 통장에 5만 3천원 있어요."

                    **조회에 실패하면 잔액 0원으로 응답하지 않습니다.** 오픈뱅킹은 실패 응답에도 금액 필드를
                    0으로 채워 보내는데, 이를 그대로 읽으면 사용자가 "잔액이 없다"고 믿게 됩니다.
                    실패는 `ACCOUNT_5001`로 명확히 구분합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`ACCOUNT_4002` 사용할 수 없는 계좌 · `ACCOUNT_4004` 기본 계좌 미설정 "
                            + "· `OPENBANK_4001` 오픈뱅킹 연결 만료"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 해당 별칭의 계좌 없음"),
            @ApiResponse(responseCode = "502", content = @Content,
                    description = "`ACCOUNT_5001` 오픈뱅킹 잔액조회 실패")
    })
    com.movi_backend.global.response.ApiResponse<BalanceResponse> getBalance(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "계좌 별칭. 생략하면 기본 계좌", example = "생활비 통장")
            String accountAlias
    );

    @Operation(
            summary = "연결 계좌 목록",
            description = """
                    오픈뱅킹으로 연결한 계좌를 모두 반환합니다. 어느 계좌가 기본 계좌인지, 어떤 별칭이
                    붙어 있는지 함께 나옵니다.

                    **계좌번호는 마스킹된 형태만 나갑니다.** 원문은 응답에 포함하지 않습니다.

                    계좌가 하나도 없으면 오류가 아니라 빈 목록과 함께 연결을 권하는 안내가 나갑니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 계좌가 없어도 200이다")
    })
    com.movi_backend.global.response.ApiResponse<AccountListResponse> getAccounts(
            @Parameter(hidden = true) AuthUser authUser
    );

    @Operation(
            summary = "기본 계좌 지정",
            description = """
                    별칭 없이 "잔액 알려줘", "엄마한테 보내줘"라고 말했을 때 쓰일 계좌를 정합니다.

                    **기본 계좌는 사용자당 하나뿐입니다.** 새로 지정하면 기존 기본 계좌는 자동으로 해제됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지정 완료"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`ACCOUNT_4002` 사용할 수 없는 계좌"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 본인 계좌가 아니거나 존재하지 않음")
    })
    com.movi_backend.global.response.ApiResponse<AccountResponse> designatePrimary(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "기본으로 지정할 계좌 ID", example = "12") Long accountId
    );

    @Operation(
            summary = "계좌 별칭 변경",
            description = """
                    계좌에 음성으로 부를 이름을 붙입니다. "국민은행 계좌"보다 "생활비 통장"이 듣는 사람에게
                    어느 계좌인지 분명합니다.

                    **같은 사용자 안에서 별칭은 중복될 수 없습니다.** 음성 별칭이 곧 조회 키이므로,
                    같은 이름의 계좌가 둘이면 어느 쪽을 말한 것인지 판단할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 완료"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` 별칭 형식 오류 · `ACCOUNT_4003` 이미 쓰고 있는 별칭"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 본인 계좌가 아니거나 존재하지 않음")
    })
    com.movi_backend.global.response.ApiResponse<AccountResponse> changeAlias(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "별칭을 바꿀 계좌 ID", example = "12") Long accountId,
            AccountAliasChangeRequest request
    );

    @Operation(
            summary = "계좌 연결 해제",
            description = """
                    연결된 계좌를 목록에서 내립니다 (명세서 1.5).

                    **행을 지우지 않고 비활성으로 표시합니다.** 이 계좌를 참조하는 거래내역과 이체 이력이
                    남아 있어야 하며, 지난 이체를 되짚을 수 없게 되면 분쟁이 났을 때 근거가 사라집니다.
                    해제한 계좌는 목록·잔액조회·이체 대상에서 모두 빠집니다.

                    **보내는 중인 이체가 걸려 있으면 해제하지 않습니다.** `PENDING`·`RISK_REVIEW` 상태의
                    이체가 있으면 `ACCOUNT_4005`로 거절합니다. 결과가 정해지지 않은 돈을 어디에도
                    귀속시킬 수 없기 때문입니다.

                    기본 계좌를 해제하면 **남은 계좌 중 하나가 자동으로 기본 계좌가 됩니다.** 기본 계좌가
                    비면 계좌를 지정하지 않은 음성 명령이 어느 계좌를 볼지 알 수 없어집니다.
                    마지막 계좌를 해제하는 것은 막지 않습니다.

                    응답은 **해제 후 남은 계좌 목록**입니다. 화면이 다시 조회하지 않아도 되도록 했습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료. 남은 계좌 목록을 돌려준다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`ACCOUNT_4002` 이미 해제된 계좌 · `ACCOUNT_4005` 진행 중인 이체가 있음"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 본인 계좌가 아니거나 존재하지 않음")
    })
    com.movi_backend.global.response.ApiResponse<AccountListResponse> disconnect(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "연결을 해제할 계좌 ID", example = "12") Long accountId
    );
}
