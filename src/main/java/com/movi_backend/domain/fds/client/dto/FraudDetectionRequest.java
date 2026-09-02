package com.movi_backend.domain.fds.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 실제 AI FDS 서버로 보내는 요청 본문. {@link TransactionData}와 마찬가지로 AI 서버 계약을
 * 그대로 옮긴 것이다.
 *
 * <p>{@code history}는 현재 항상 빈 배열로 보낸다. {@code transactions} 테이블에는 상대방
 * 은행 코드가 저장되지 않아(계좌번호만 있다) 과거 거래를 이 스키마로 정확히 재구성할 수
 * 없다 — 은행 코드를 지어내면 AI의 z-score·패턴 피처가 실제와 다른 값으로 왜곡되므로,
 * 잘못된 값을 보내느니 빈 이력을 보내는 쪽을 택했다. 과거 거래를 채우려면
 * {@code transactions}에 상대 은행 코드 컬럼을 추가하는 스키마 변경이 먼저 필요하다.
 */
public record FraudDetectionRequest(
        @JsonProperty("current_transaction") TransactionData currentTransaction,
        List<TransactionData> history
) {

    public static FraudDetectionRequest of(final TransactionData currentTransaction) {
        return new FraudDetectionRequest(currentTransaction, List.of());
    }
}
