package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.DirectTransferService;
import com.movi_backend.domain.transfer.application.TransferQueryService;
import com.movi_backend.domain.transfer.application.BankDirectory;
import com.movi_backend.domain.transfer.application.TransferRecipientCommandService;
import com.movi_backend.domain.transfer.application.TransferRecipientQueryService;
import com.movi_backend.domain.transfer.controller.docs.TransferApiDocs;
import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.request.TransferReviewRequest;
import com.movi_backend.domain.transfer.dto.response.BankListResponse;
import com.movi_backend.domain.transfer.dto.response.RecipientListResponse;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.dto.response.TransferResultResponse;
import com.movi_backend.domain.transfer.dto.response.TransferReviewResponse;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController implements TransferApiDocs {

    private final TransferQueryService transferQueryService;
    private final TransferRecipientQueryService transferRecipientQueryService;
    private final TransferRecipientCommandService transferRecipientCommandService;
    private final DirectTransferService directTransferService;
    private final BankDirectory bankDirectory;

    /** 상대방을 등록할 때 고를 은행 목록. 계좌번호 앞자리로 은행을 추정하지 않는다 */
    @GetMapping("/banks")
    public ApiResponse<BankListResponse> getBanks() {
        return ApiResponse.success(
                BankListResponse.from(bankDirectory.findAll()),
                "보내실 곳의 은행을 골라 주세요."
        );
    }

    /** 등록 수취인 목록. 직접 입력 송금에서 "누구에게"를 고르는 선택지다 */
    @GetMapping("/recipients")
    public ApiResponse<RecipientListResponse> getRecipients(
            @CurrentUser final AuthUser authUser
    ) {
        final RecipientListResponse recipients = transferRecipientQueryService.findAll(
                authUser.userId()
        );
        return ApiResponse.success(recipients, recipients.toVoiceMessage());
    }

    /** 상대방 등록. 이름으로 부르려면 먼저 이름과 계좌가 묶여 있어야 한다 */
    @PostMapping("/recipients")
    public ApiResponse<RecipientResponse> registerRecipient(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final RecipientRegisterRequest request
    ) {
        final RecipientResponse recipient = transferRecipientCommandService.register(
                authUser.userId(),
                request
        );
        /*
         * 저장된 내용을 그대로 되읽어 준다. 화면을 볼 수 없는 사용자에게는 이 문장이 무엇이
         * 저장됐는지 확인할 유일한 수단이고, 예금주는 사용자가 적은 값이 아니라 조회로
         * 확인된 이름이라 여기서 처음 듣는다.
         *
         * 계좌번호는 마스킹한 값만 읽는다. 원문은 응답 본문에 남기지 않는다는 규칙이 있고,
         * 잘못된 계좌로 나가는 것을 막는 확인은 송금 직전 복창이 맡는다 — 그때는 전체
         * 자릿수를 하나씩 읽어 준다.
         */
        return ApiResponse.success(
                recipient,
                "%s 님을 저장했어요. %s 예금주 %s 님, 계좌번호 %s이에요. 이제 이름만 부르셔도 보낼 수 있어요."
                        .formatted(
                                recipient.nickname(),
                                bankDirectory.displayNameOf(recipient.bankCode()),
                                recipient.holderName(),
                                recipient.maskedAccountNumber()
                        )
        );
    }

    /** 직접 입력 송금 검토. 확인 ID만 발급하고 이체하지 않는다 */
    @PostMapping("/review")
    public ApiResponse<TransferReviewResponse> review(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final TransferReviewRequest request
    ) {
        final TransferReviewResponse review = directTransferService.review(
                authUser.userId(),
                request
        );
        return ApiResponse.success(review, review.toVoiceMessage());
    }

    /** 검토한 송금 실행 */
    @PostMapping
    public ApiResponse<TransferResultResponse> execute(
            @CurrentUser final AuthUser authUser,
            @Valid @RequestBody final TransferExecuteRequest request
    ) {
        final TransferResultResponse result = directTransferService.execute(
                authUser.userId(),
                request
        );
        return ApiResponse.success(result, result.toVoiceMessage());
    }

    @GetMapping("/status")
    public ApiResponse<TransferStatusResponse> getStatus(
            @CurrentUser final AuthUser authUser,
            @RequestParam final String idempotencyKey
    ) {
        final TransferStatusResponse response = transferQueryService.findStatus(
                authUser.userId(),
                idempotencyKey
        );
        return ApiResponse.success(response, response.toVoiceMessage());
    }
}
