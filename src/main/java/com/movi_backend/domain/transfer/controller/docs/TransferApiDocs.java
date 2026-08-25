package com.movi_backend.domain.transfer.controller.docs;

import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 송금 상태 조회 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code TransferController}에 있다.
 */
@Tag(
        name = "송금",
        description = "송금 실행은 음성 명령 API에서 일어난다. 여기서는 결과를 확인한다."
)
public interface TransferApiDocs {

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
