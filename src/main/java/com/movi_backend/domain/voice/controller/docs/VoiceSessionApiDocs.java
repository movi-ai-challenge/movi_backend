package com.movi_backend.domain.voice.controller.docs;

import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.domain.voice.dto.request.VoiceSessionStartRequest;
import com.movi_backend.domain.voice.dto.response.VoiceSessionStartResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

/**
 * 음성 세션·명령 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code VoiceSessionController}에 있다.
 */
@Tag(
        name = "음성 명령",
        description = "이 서비스의 핵심 경로. 발화 하나로 끝나지 않고 세션 위에서 대화가 이어진다."
)
public interface VoiceSessionApiDocs {

    @Operation(
            summary = "음성 세션 시작",
            description = """
                    대화 세션을 만듭니다. 이후 발화는 모두 이 세션 ID로 올립니다.

                    **세션은 슬롯의 단일 소유자입니다.** "엄마한테"까지만 말하고 금액을 빠뜨렸을 때 그 정보를
                    들고 있는 주체가 세션입니다. 프론트와 AI는 앞선 발화의 금액·수취인을 보관하지 않습니다.

                    유효시간은 마지막 활동 후 5분입니다. 재질문이나 확인을 기다리는 동안은 60초로 짧아집니다.

                    **본문은 선택입니다.** `deviceUuid`를 보내면 그 기기가 세션에 연결돼 이 세션에서 시작된
                    이체의 FDS 위험도 평가에 신뢰 기기 여부로 들어갑니다. 기기는 PIN 인증(로그인 또는 최초
                    등록)을 통과한 시점에 신뢰 기기로 등록되므로, **같은 `deviceUuid`를 그때도 함께 보내야**
                    합니다. 보내지 않거나 등록되지 않은 기기면 비신뢰로 평가돼 위험도가 한 단계 올라갑니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "세션 생성. `voiceSessionId`를 이후 요청에 쓴다"),
            @ApiResponse(responseCode = "401", content = @Content,
                    description = "`AUTH_4010` 인증 필요")
    })
    com.movi_backend.global.response.ApiResponse<VoiceSessionStartResponse> start(
            @Parameter(hidden = true) AuthUser authUser,
            VoiceSessionStartRequest request
    );

    @Operation(
            summary = "음성 명령 전송",
            description = """
                    녹음한 발화를 올립니다. 백엔드가 AI Voice API로 분석을 맡기고, **그 결과를 검증한 뒤**
                    다음 행동을 정합니다.

                    ## 응답의 `state`로 다음 행동이 정해집니다

                    | state | 뜻 | 프론트가 할 일 |
                    |---|---|---|
                    | `CLARIFYING` | 정보가 부족함 | `voiceMessage`를 읽어 주고 같은 세션에 다음 발화 업로드 |
                    | `AWAITING_CONFIRMATION` | 확인 대기 | 확인 문장을 읽어 주고, **UUID 하나를 만들어** 보관 |
                    | `COMPLETED` | 송금 완료 | 결과 안내 |
                    | `CANCELED` | 취소됨 | 취소 안내 |

                    ## 확인과 멱등성

                    `AWAITING_CONFIRMATION` 응답에는 `confirmationId`가 들어 있습니다. **확인 발화를 올릴 때
                    이 값을 그대로 되돌려 보내야** 합니다. 백엔드가 "지금 확인하려는 것이 방금 안내한 그 송금이
                    맞는지" 대조하는 값이라, 확인 대기 중 금액이나 수취인이 바뀌면 기존 값은 폐기됩니다.

                    함께 프론트가 UUID를 하나 만들어 `idempotencyKey`로 보냅니다. 타임아웃으로 재시도할 때도
                    **같은 키**를 씁니다. 새 키를 만들면 중복 이체가 됩니다.

                    응답을 못 받았다면 `GET /api/transfers/status?idempotencyKey=...`로 결과를 확인하세요.

                    ## 지원하는 명령

                    잔액조회, 송금, 거래내역 조회, 확인, 취소입니다. 이 밖의 의도는 `VOICE_4003`으로 거부합니다.

                    ## 음성 파일 제약

                    WebM/Opus, WAV 또는 Safari/iOS MP4·M4A, **최대 5MB·15초**입니다.
                    재생 시간은 헤더에서 읽으므로
                    형식이 올바르지 않으면 업로드 단계에서 거부됩니다.

                    ## 알아 둘 것

                    - **직접 계좌번호를 말하는 송금은 거부합니다.** 미리 등록한 수취인만 쓸 수 있습니다
                    - 인식 신뢰도가 낮으면 실행하지 않고 다시 말해 달라고 합니다
                    - 확인 대기 중 금액이나 수취인이 바뀌면 기존 확인 정보를 버리고 새로 확인받습니다
                    - **고위험 송금은 403 `FDS_4031`로 차단**되며 실제 이체는 일어나지 않습니다
                    - 위험도 평가에 실패해도 이체하지 않습니다. 안전 확인 없이 돈을 보내지 않습니다
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "분석 완료. `state`에 따라 재질문·확인 대기·완료·취소로 나뉜다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`VOICE_4003` 알 수 없는 의도 · `VOICE_4004` 낮은 인식 신뢰도 "
                            + "· `VOICE_4005` 세션 만료 · `VOICE_4006` 재질문 횟수 초과 "
                            + "· `VOICE_4007` 처리할 수 없는 세션 상태 · `VOICE_4009` 15초 초과 "
                            + "· `VOICE_4011` 확인 정보 불일치 "
                            + "· `TRANSFER_4001` 한도 초과 · `TRANSFER_4002` 잔액 부족"),
            @ApiResponse(responseCode = "403", content = @Content,
                    description = "`FDS_4031` 고위험으로 차단(이체 미실행) · `SRV_4030` 다른 사용자의 세션"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`VOICE_4040` 세션 없음 · `TRANSFER_4041` 등록되지 않은 수취인"),
            @ApiResponse(responseCode = "409", content = @Content,
                    description = "`TRANSFER_4090` 같은 키로 이미 처리 중"),
            @ApiResponse(responseCode = "415", content = @Content,
                    description = "`SRV_4150` 지원하지 않는 음성 형식"),
            @ApiResponse(responseCode = "502", content = @Content,
                    description = "`VOICE_5000` 음성 인식 실패 · `FDS_5000` 위험도 평가 실패(이체 미실행)"),
            @ApiResponse(responseCode = "504", content = @Content,
                    description = "`FDS_5001` 위험도 평가 지연(이체 미실행)")
    })
    com.movi_backend.global.response.ApiResponse<VoiceCommandResponse> command(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "음성 세션 ID", example = "15") Long voiceSessionId,
            @Parameter(description = "녹음 파일. WebM/Opus, WAV 또는 MP4·M4A, 최대 5MB·15초")
            MultipartFile audio,
            @Parameter(description = "확인 발화에만 보낸다. 확인 대기 응답으로 받은 값을 그대로 되돌려 준다")
            String confirmationId,
            @Parameter(description = "확인 발화에만 보낸다. 확인 화면에서 만든 UUID를 재시도에도 그대로 쓴다")
            String idempotencyKey
    );
}
