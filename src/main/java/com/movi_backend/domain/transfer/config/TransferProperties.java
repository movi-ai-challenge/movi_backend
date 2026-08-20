package com.movi_backend.domain.transfer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 이체 관련 설정.
 *
 * @param confirmationExpireMinutes 고위험 감지 후 본인 재확인을 기다리는 시간(분).
 *                                  <b>무기한 열어 두지 않는다.</b> 확인 대기 건이 방치되면
 *                                  한참 뒤에 "네"가 들어와 엉뚱한 시점에 돈이 나간다.
 *                                  시간이 지나면 차단으로 확정한다.
 */
@Validated
@ConfigurationProperties(prefix = "movi.transfer")
public record TransferProperties(
        Integer confirmationExpireMinutes,
        @Min(1) long minimumAmount,
        @Min(1) long perTransferLimit,
        @Min(1) long dailyLimit
) {

    private static final int DEFAULT_CONFIRMATION_EXPIRE_MINUTES = 5;

    public TransferProperties {
        if (confirmationExpireMinutes == null || confirmationExpireMinutes <= 0) {
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
