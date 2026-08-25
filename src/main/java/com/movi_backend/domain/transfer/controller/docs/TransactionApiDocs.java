package com.movi_backend.domain.transfer.controller.docs;

import com.movi_backend.domain.transfer.dto.response.TransactionDetailResponse;
import com.movi_backend.domain.transfer.dto.response.TransactionResponse;
import com.movi_backend.domain.transfer.type.TransactionType;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

/**
 * 거래내역 API 문서.
 *
 * <p>Swagger 어노테이션만 담는다. 구현은 {@code TransactionController}에 있다.
 */
@Tag(
        name = "거래내역",
        description = "계좌의 입출금 내역 조회. 음성으로 물어보는 경로는 음성 명령 API에 있다."
)
public interface TransactionApiDocs {

    @Operation(
            summary = "거래내역 조회",
            description = """
                    계좌의 거래내역을 최신순으로 반환합니다. 기간·입출금 유형으로 거를 수 있습니다.

                    `accountId`를 생략하면 기본 계좌를 조회합니다.

                    기간을 둘 다 주면 **시작일이 종료일보다 늦을 수 없습니다.** 페이지 크기는 최대 100건입니다.

                    **계좌번호와 상대방 계좌번호는 응답에 포함하지 않습니다.** 목록에 필요하지 않고,
                    음성으로 읽어 줄 값도 아니기 때문입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 거래가 없어도 200이다"),
            @ApiResponse(responseCode = "400", content = @Content,
                    description = "`SRV_4000` 기간 역전 또는 페이지 범위 오류 · `ACCOUNT_4002` 사용할 수 없는 계좌 "
                            + "· `ACCOUNT_4004` 기본 계좌 미설정"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`ACCOUNT_4040` 본인 계좌가 아니거나 존재하지 않음")
    })
    com.movi_backend.global.response.ApiResponse<PageResponse<TransactionResponse>> getTransactions(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "조회할 계좌 ID. 생략하면 기본 계좌", example = "12") Long accountId,
            @Parameter(description = "조회 시작일(포함)", example = "2026-08-01") LocalDate startDate,
            @Parameter(description = "조회 종료일(포함)", example = "2026-08-25") LocalDate endDate,
            @Parameter(description = "IN은 입금, OUT은 출금. 생략하면 전체") TransactionType type,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0") int page,
            @Parameter(description = "페이지당 건수. 최대 100", example = "20") int size
    );

    @Operation(
            summary = "거래내역 상세 조회",
            description = """
                    거래 한 건의 상세를 반환합니다. 목록에서 본 거래의 **거래 후 잔액·메모·분류**를
                    확인할 때 씁니다.

                    `voiceMessage`에 날짜·상대방·금액과 거래 뒤 잔액이 한국어로 담깁니다 —
                    "8월 24일 김영희 님에게 5만원 보냈어요. 거래 뒤 잔액은 95만원이에요."
                    상세를 여는 이유가 대개 "그래서 남은 게 얼마인가"라서 잔액까지 읽어 줍니다.

                    **없는 거래와 남의 거래에 같은 응답을 줍니다.** 구분해서 알려주면 ID를 훑어
                    남의 거래가 존재하는지 알아낼 수 있기 때문입니다.

                    **상대방 계좌번호는 포함하지 않습니다.** 음성으로 읽어 줄 값이 아니고,
                    사용자에게 쓸모없이 주변에만 들립니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", content = @Content,
                    description = "`TRANSFER_4042` 거래가 없거나 본인 것이 아님")
    })
    com.movi_backend.global.response.ApiResponse<TransactionDetailResponse> getTransaction(
            @Parameter(hidden = true) AuthUser authUser,
            @Parameter(description = "조회할 거래 ID", example = "101") Long transactionId
    );
}
