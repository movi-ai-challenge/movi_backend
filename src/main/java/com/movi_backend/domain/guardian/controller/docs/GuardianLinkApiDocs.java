package com.movi_backend.domain.guardian.controller.docs;

import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * 보호자 등록 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code GuardianLinkController}에 있다.
 */
@Tag(
        name = "보호자",
        description = """
                이상 거래를 문자로 알릴 보호자를 등록한다.

                **문자를 보내는 API는 따로 없다.** 발송은 이체 위험도 판정에 딸려 자동으로
                일어나는 내부 동작이라 외부에 노출되는 것은 이 등록 하나뿐이다.
                """
)
public interface GuardianLinkApiDocs {

    @Operation(summary = "내 보호자 연결 조회")
    com.movi_backend.global.response.ApiResponse<List<GuardianLinkRegisterResponse>> findActiveLinks(
            @Parameter(hidden = true) AuthUser authUser
    );

    @Operation(
            summary = "보호자 등록",
            description = """
                    이상 거래가 감지되면 경고 문자를 받을 보호자를 등록합니다.

                    **확인 절차가 없습니다.** 응답이 오면 연결은 이미 `ACTIVE`이고, 그 시점부터
                    바로 알림 대상이 됩니다. 보호자가 Movi 회원일 필요도 없습니다 — 알림은
                    앱이 아니라 전화번호로 나갑니다.

                    **등록해야 문자가 나갑니다.** 활성 연결이 하나도 없으면 고위험 이체가
                    감지돼도 보낼 곳이 없어 알림 없이 지나갑니다.

                    문자가 나가는 시점은 이체의 FDS 위험도가 `MEDIUM` 또는 `HIGH`일 때입니다.
                    `HIGH`는 이체가 차단되고, `MEDIUM`은 이체가 완료된 뒤 알림만 나갑니다.
                    `LOW`에는 아무것도 보내지 않습니다.

                    `guardianPhone`은 `010-1234-5678`, `01012345678`, `+82 10 1234 5678`을
                    모두 받습니다. 서버가 정규화하므로 프런트에서 형식을 맞출 필요가 없습니다.
                    `relation`("자녀", "배우자" 등)은 비워도 됩니다.

                    누구의 보호자인지는 인증 정보로 정합니다. 요청 본문에 대상 사용자를 넣는
                    필드는 없습니다.

                    **응답에 전화번호는 담기지 않습니다.** 저장은 암호화해서 하고, 등록한 번호를
                    되읽는 경로는 두지 않았습니다. 화면에 다시 보여줘야 한다면 프런트가 입력값을
                    들고 있어야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "등록 성공. `status`는 항상 `ACTIVE`다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = """
                            `NOTI_4001` 휴대전화 번호 형식이 아님 ·
                            `GUARDIAN_4001` 이미 등록된 번호 ·
                            `GUARDIAN_4004` 본인 번호 ·
                            `REQ_4000` 이름 누락·50자 초과, 번호 누락, 관계 30자 초과
                            """),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 인증 필요"),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`AUTH_4030` 휴면·탈퇴 계정"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`AUTH_4040` 회원 없음")
    })
    com.movi_backend.global.response.ApiResponse<GuardianLinkRegisterResponse> register(
            @Parameter(hidden = true) AuthUser authUser,
            GuardianLinkCreateRequest request
    );
}
