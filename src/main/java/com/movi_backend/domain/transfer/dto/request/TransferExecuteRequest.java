package com.movi_backend.domain.transfer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 이체 실행 요청.
 *
 * <p>수취인은 두 방법 중 하나로 지정한다. 저장된 수취인({@code recipientId}) 또는 계좌 직접 입력
 * ({@code toBankCode} + {@code toAccountNum} + {@code toHolderName}).
 *
 * <p><b>AI가 채운 값을 그대로 믿지 않는다.</b> 음성에서 추출한 값이든 화면 입력이든 여기서 다시
 * 검증한다. AI가 금액을 환각으로 채워 넣거나 놓쳐도 이 지점에서 막혀야 한다.
 *
 * @param idempotencyKey 중복 발화 방지 키. 같은 키로 다시 오면 이체를 재실행하지 않는다
 * @param trustedDevice  등록된 기기 여부. 미지정이면 {@code false}로 본다(안전한 쪽)
 * @param sttConfidence  음성 인식 신뢰도 0~1. 화면 조작이면 미지정(1.0으로 본다)
 */
public record TransferExecuteRequest(
        @NotBlank
        @Size(max = 64)
        String idempotencyKey,

        @NotNull
        Long fromAccountId,

        Long recipientId,

        @Size(max = 10)
        String toBankCode,

        String toAccountNum,

        @Size(max = 50)
        String toHolderName,

        @NotNull
        @Positive
        Long amount,

        Boolean trustedDevice,

        Double sttConfidence
) {

    private static final double DEFAULT_STT_CONFIDENCE = 1.0d;

    /** 저장된 수취인을 쓰는 요청인지 여부 */
    public boolean usesSavedRecipient() {
        return recipientId != null;
    }

    public boolean hasDirectAccount() {
        return isPresent(toBankCode) && isPresent(toAccountNum) && isPresent(toHolderName);
    }

    public boolean trustedDeviceOrFalse() {
        if (trustedDevice == null) {
            return false;
        }
        return trustedDevice;
    }

    public double sttConfidenceOrDefault() {
        if (sttConfidence == null) {
            return DEFAULT_STT_CONFIDENCE;
        }
        return sttConfidence;
    }

    private boolean isPresent(final String value) {
        return value != null && !value.isBlank();
    }
}
