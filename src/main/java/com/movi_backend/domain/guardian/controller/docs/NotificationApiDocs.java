package com.movi_backend.domain.guardian.controller.docs;

import com.movi_backend.domain.guardian.dto.response.NotificationResponse;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 보호자 알림 기록 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code NotificationController}에 있다.
 */
@Tag(
        name = "보호자 알림",
        description = "위험 이체가 보호자에게 어떻게 통보됐는지 확인한다."
)
public interface NotificationApiDocs {

    @Operation(
            summary = "보호자 알림 발송 기록 조회",
            description = """
                    내가 관련된 알림을 최신순으로 반환합니다. 두 방향을 함께 봅니다 —
                    **내 이체 때문에 나간 알림**과 **내가 보호자로 받은 알림**입니다.

                    수신자만으로 거르지 않는 이유는 미가입 보호자에게 간 알림이 빠지기 때문입니다.
                    보호자 계정은 초대를 수락해야 연결되므로, 그 전까지 수신자는 비어 있습니다.

                    `status`가 `SENT`면 제공자가 접수한 것이고 `providerMsgId`로 추적할 수 있습니다.
                    `FAILED`라도 `nextRetryAt`이 있으면 재시도가 남아 있는 상태입니다.

                    **전화번호는 마스킹해서 내려갑니다.** 페이지 크기는 최대 100건입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 알림이 없어도 200이다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`REQ_4000` 페이지 범위 오류"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 인증 필요")
    })
    com.movi_backend.global.response.ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "0부터 시작하는 페이지 번호") int page,
            @Parameter(description = "페이지 크기 (최대 100)") int size
    );
}
