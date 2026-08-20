package com.movi_backend.domain.transfer.dto;

import com.movi_backend.domain.transfer.dto.response.TransferResponse;

/**
 * 재확인 요청을 받았을 때의 이체 상태.
 *
 * <p>세 갈래를 예외 없이 표현한다. 특히 <b>만료를 예외로 던지지 않는다.</b> 만료 시점에는 이체를
 * {@code BLOCKED}로 확정하고 보호자에게도 알려야 하는데, 예외를 던져 트랜잭션이 롤백되면 그
 * 확정이 사라진다.
 *
 * @param prepared          진행 가능한 경우의 이체 정보. 그 외에는 {@code null}
 * @param completedResponse 이미 완료된 건을 다시 확인한 경우의 기존 결과. 그 외에는 {@code null}
 * @param expired           확인 시간이 지나 차단으로 확정된 경우
 */
public record TransferConfirmation(
        PreparedTransfer prepared,
        TransferResponse completedResponse,
        boolean expired
) {

    public static TransferConfirmation ready(final PreparedTransfer prepared) {
        return new TransferConfirmation(prepared, null, false);
    }

    public static TransferConfirmation alreadyCompleted(final TransferResponse completedResponse) {
        return new TransferConfirmation(null, completedResponse, false);
    }

    /**
     * 확인 시간이 지나 차단으로 확정된 결과.
     *
     * <p>이름을 {@code expired()}로 두면 record 컴포넌트 접근자와 시그니처가 겹친다.
     */
    public static TransferConfirmation expiredConfirmation() {
        return new TransferConfirmation(null, null, true);
    }

    /** 같은 확인 요청이 두 번 들어온 경우. 이체를 다시 실행하지 않고 기존 결과를 돌려준다. */
    public boolean isAlreadyCompleted() {
        return completedResponse != null;
    }
}
