package com.movi_backend.domain.transfer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "movi.transfer")
public record TransferProperties(
        @Min(1) long minimumAmount,
        @Min(1) long perTransferLimit,
        @Min(1) long dailyLimit,
        @Min(1) int confirmationExpireMinutes
) {

    /**
     * 화면 검토 확인의 기본 유효시간.
     *
     * <p>음성 확인 대기(60초)보다 길다. 음성은 확인 문장을 듣고 바로 대답하지만, 화면 검토는
     * 사용자가 금액과 수취인을 눈이나 스크린리더로 훑어 읽는 시간이 필요하다.
     */
    private static final int DEFAULT_CONFIRMATION_EXPIRE_MINUTES = 5;

    public TransferProperties {
        if (confirmationExpireMinutes <= 0) {
            confirmationExpireMinutes = DEFAULT_CONFIRMATION_EXPIRE_MINUTES;
        }
        if (minimumAmount > perTransferLimit) {
            throw new IllegalArgumentException("최소 이체 금액은 1회 이체 한도보다 클 수 없습니다.");
        }
        if (perTransferLimit > dailyLimit) {
            throw new IllegalArgumentException("1회 이체 한도는 일일 이체 한도보다 클 수 없습니다.");
        }
    }
}
