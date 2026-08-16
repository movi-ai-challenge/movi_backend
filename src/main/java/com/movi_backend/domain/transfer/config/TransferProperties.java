package com.movi_backend.domain.transfer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "movi.transfer")
public record TransferProperties(
        @Min(1) long minimumAmount,
        @Min(1) long perTransferLimit,
        @Min(1) long dailyLimit
) {

    public TransferProperties {
        if (minimumAmount > perTransferLimit) {
            throw new IllegalArgumentException("최소 이체 금액은 1회 이체 한도보다 클 수 없습니다.");
        }
        if (perTransferLimit > dailyLimit) {
            throw new IllegalArgumentException("1회 이체 한도는 일일 이체 한도보다 클 수 없습니다.");
        }
    }
}
