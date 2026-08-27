package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.DirectTransferService;
import com.movi_backend.domain.transfer.application.TransferQueryService;
import com.movi_backend.domain.transfer.application.TransferRecipientQueryService;
import com.movi_backend.domain.transfer.controller.docs.TransferApiDocs;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.request.TransferReviewRequest;
import com.movi_backend.domain.transfer.dto.response.RecipientListResponse;
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
    private final DirectTransferService directTransferService;

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
