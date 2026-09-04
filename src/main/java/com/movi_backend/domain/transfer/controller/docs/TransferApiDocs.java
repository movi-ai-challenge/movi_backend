package com.movi_backend.domain.transfer.controller.docs;

import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.request.TransferReviewRequest;
import com.movi_backend.domain.transfer.dto.response.BankListResponse;
import com.movi_backend.domain.transfer.dto.response.RecipientListResponse;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.dto.response.TransferResultResponse;
import com.movi_backend.domain.transfer.dto.response.TransferReviewResponse;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 송금 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code TransferController}에 있다.
 */
@Tag(
        name = "송금",
        description = """
                송금 경로는 둘이다. 음성 명령 API의 확인 발화, 그리고 여기의 검토·실행이다.
                두 경로 모두 같은 한도·FDS·멱등성 검증을 지난다.
                """
)
public interface TransferApiDocs {

    @Operation(
            summary = "등록 수취인 목록",
            description = """
                    사용자가 **이름을 지어 등록한** 상대 전부입니다.

                    등록하지 않은 계좌로 한 번 보낼 때 만들어지는 거래 상대 신원 행은
                    이 목록에 넣지 않습니다. 사용자가 짓지 않은 이름이라 읽어 줄 말이 없고,
                    보낸 적도 없는 상대가 쌓이면 고를 수가 없습니다.

                    여기 없는 사람에게도 **은행과 계좌번호를 말하면 보낼 수 있습니다.**
                    이름으로 부르려면 등록이 필요합니다.

                    계좌번호는 뒤 네 자리만 남긴 `maskedAccountNumber`로 내려갑니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 없으면 빈 목록")
    })
    com.movi_backend.global.response.ApiResponse<RecipientListResponse> getRecipients(
            @Parameter(hidden = true) AuthUser authUser
    );

    @Operation(
            summary = "은행 목록",
            description = """
                    상대방을 등록할 때 고를 수 있는 은행입니다. 이름순입니다.

                    화면이 목록을 따로 들고 있으면 백엔드에서 코드를 고쳤을 때 옛 코드를
                    그대로 보내게 됩니다. 계좌번호 앞자리로 은행을 추정하지 않기로 한 이상,
                    사용자가 고를 목록은 백엔드가 줍니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    com.movi_backend.global.response.ApiResponse<BankListResponse> getBanks();

    @Operation(
            summary = "상대방 등록",
            description = """
                    음성으로 이름만 불러 송금하려면 이름과 계좌가 미리 묶여 있어야 합니다.
                    이 API 가 그 묶음을 만듭니다.

                    받는 값은 **이름·은행코드·계좌번호**입니다. 은행은 `GET /api/transfers/banks`
                    에서 고른 코드를 그대로 보냅니다 — 계좌번호 앞자리로 은행을 추정하지
                    않습니다. 앞자리가 같은 다른 은행 계좌가 걸립니다.

                    **예금주는 받지 않습니다.** 예금주조회로 확인된 이름만 씁니다. 사용자가
                    적은 이름을 그대로 쓰면 틀린 이름이 확인 복창에서 읽히고, 사용자는 맞는
                    사람에게 보내는 것으로 듣습니다.

                    확인되지 않은 계좌는 등록되지 않습니다(`TRANSFER_4011`).

                    이미 그 계좌로 보낸 적이 있으면 **그때 만들어진 행에 이름이 붙습니다.**
                    새 행을 만들면 이체 횟수가 쪼개져 FDS 가 여러 번 보낸 상대를 처음 보는
                    상대로 봅니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400",
                    description = "계좌번호 형식 오류(TRANSFER_4008), 은행 누락(TRANSFER_4010), "
                            + "예금주를 확인하지 못함(TRANSFER_4011), 본인 계좌(TRANSFER_4009)"),
            @ApiResponse(responseCode = "409",
                    description = "이미 쓰고 있는 이름(TRANSFER_4091), "
                            + "이미 등록된 계좌(TRANSFER_4092)")
    })
    com.movi_backend.global.response.ApiResponse<RecipientResponse> registerRecipient(
            @Parameter(hidden = true) AuthUser authUser,
            RecipientRegisterRequest request
    );

    @Operation(
            summary = "직접 입력 송금 검토",
            description = """
                    보낼 내용을 검증하고 확인 ID를 발급합니다. **이 시점에는 돈이 나가지 않습니다.**

                    소유권(계좌·수취인), 계좌 활성 여부, 1회 한도를 검증합니다. 잔액과 일일 한도,
                    FDS는 실행 시점에 확인합니다 — 검토와 실행 사이에 달라질 수 있는 값이라
                    검토 때 통과시켜도 보장이 되지 않습니다.

                    `fromAccountId`를 비우면 기본 계좌에서 나갑니다.

                    응답의 `confirmationId`를 보관하고 UUID `idempotencyKey`를 **하나** 만드세요.
                    사용자가 화면에서 명시적으로 확인한 뒤 실행 API에 두 값을 함께 보냅니다.
                    확인은 `expiresAt`까지만 유효합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검토 성공. 아직 이체되지 않았다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = """
                            `TRANSFER_4002` 금액 범위 오류 ·
                            `TRANSFER_4003` 1회 한도 초과 ·
                            `ACCOUNT_4002` 사용할 수 없는 계좌 ·
                            `ACCOUNT_4004` 기본 계좌 미지정
                            """),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`AUTH_4030` 다른 사용자의 계좌·수취인"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 계좌 없음 · `TRANSFER_4041` 수취인 없음")
    })
    com.movi_backend.global.response.ApiResponse<TransferReviewResponse> review(
            @Parameter(hidden = true) AuthUser authUser,
            TransferReviewRequest request
    );

    @Operation(
            summary = "직접 입력 송금 실행",
            description = """
                    검토한 송금을 실행합니다. **여기서 실제로 돈이 나갑니다.**

                    금액·수취인·출금 계좌를 다시 보내지 않습니다. 서버가 검토 시점의 스냅샷을
                    들고 있으므로 이 요청은 "그 확인을 실행한다"는 뜻만 갖습니다.

                    확인 하나는 **멱등성 키 하나**에만 묶입니다. 같은 확인을 다른 키로 두 번
                    실행하려는 시도는 `TRANSFER_4007`로 거부됩니다. 반대로 **같은 키의 재시도는
                    통과**하며 이미 끝난 송금의 결과를 그대로 돌려줍니다.

                    응답을 받지 못한 타임아웃에서는 새 키를 만들지 말고 `GET /api/transfers/status`
                    로 같은 키를 조회하거나 같은 키로 다시 요청하세요.

                    차단(`BLOCKED`)과 실패(`FAILED`)도 200으로 내려갑니다. 사용자에게는 "왜 돈이
                    나가지 않았는지"가 결과이기 때문입니다. `status`와 `riskLevel`을 그대로
                    표시하고 프런트가 성공 여부를 따로 판단하지 마세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "실행 결과. `COMPLETED`/`BLOCKED`/`FAILED`를 모두 포함한다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = """
                            `TRANSFER_4007` 확인 없음·만료·다른 키로 이미 실행 ·
                            `TRANSFER_4001` 잔액 부족 ·
                            `TRANSFER_4004` 1일 한도 초과 ·
                            `REQ_4000` 멱등성 키 형식 오류(UUID여야 한다)
                            """),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`AUTH_4030` 다른 사용자의 계좌·수취인"),
            @ApiResponse(responseCode = "409", content = @Content,
                    description = "`TRANSFER_4090` 같은 키의 이체가 아직 처리 중"),
            @ApiResponse(responseCode = "502", content = @Content,
                    description = "`FDS_5000` 위험도 평가 실패. **이체하지 않는다**"),
            @ApiResponse(responseCode = "504", content = @Content,
                    description = "`FDS_5001` 위험도 평가 지연. **이체하지 않는다**")
    })
    com.movi_backend.global.response.ApiResponse<TransferResultResponse> execute(
            @Parameter(hidden = true) AuthUser authUser,
            TransferExecuteRequest request
    );

    @Operation(
            summary = "송금 상태 조회",
            description = """
                    멱등성 키로 송금 결과를 조회합니다. **네트워크 타임아웃 등으로 확인 응답을 받지 못했을 때
                    쓰는 복구 경로**입니다.

                    이때 새 키를 만들면 안 됩니다. 확인 요청에 썼던 키를 그대로 넣어 조회해야
                    같은 송금의 결과를 볼 수 있습니다. 새 키로 다시 송금하면 **중복 이체**가 됩니다.

                    반환 상태의 의미는 다음과 같습니다.

                    | 상태 | 뜻 |
                    |---|---|
                    | `PENDING` | 접수됐지만 아직 위험도 평가 전 |
                    | `RISK_REVIEW` | FDS 평가 중 |
                    | `COMPLETED` | 송금 완료 — 돈이 나갔다 |
                    | `BLOCKED` | 고위험으로 차단 — 돈이 나가지 않았다 |
                    | `FAILED` | 실행 실패 — 돈이 나가지 않았다 |
                    | `CANCELED` | 사용자가 취소 |

                    **인증 사용자와 키가 모두 일치하는 송금만 반환합니다.** 계좌번호는 포함하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` 멱등성 키 형식 오류(UUID여야 한다)"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`TRANSFER_4040` 해당 키의 송금 없음. 본인 것이 아닌 경우도 포함한다")
    })
    com.movi_backend.global.response.ApiResponse<TransferStatusResponse> getStatus(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(
                    description = "확인 요청에 사용한 멱등성 키(UUID). 새로 만들지 말고 그대로 넣는다",
                    example = "c14c5b4d-a394-4d67-8788-bc716e5a60b6"
            )
            String idempotencyKey
    );
}
