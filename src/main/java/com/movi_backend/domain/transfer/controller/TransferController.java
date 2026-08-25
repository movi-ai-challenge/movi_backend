package com.movi_backend.domain.transfer.controller;

import com.movi_backend.domain.transfer.application.TransferQueryService;
import com.movi_backend.domain.transfer.controller.docs.TransferApiDocs;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController implements TransferApiDocs {

    private final TransferQueryService transferQueryService;

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
