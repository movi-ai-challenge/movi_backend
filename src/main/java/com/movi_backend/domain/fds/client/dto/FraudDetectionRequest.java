package com.movi_backend.domain.fds.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 실제 AI FDS 서버로 보내는 요청 본문. {@link TransactionData}와 마찬가지로 AI 서버 계약을
 * 그대로 옮긴 것이다.
 *
 * <p>{@code history}에는 같은 출금계좌의 최근 출금을 실어 보낸다. 이력이 비면 과거 대비
 * 비율을 보는 AI 규칙({@code HIGH_AMOUNT_RATIO} 등)이 발동하지 않아 금액과 무관하게 LOW 만
 * 나온다 — {@link com.movi_backend.domain.fds.client.dto.FdsHistoryEntry} 에 실측값을 적어
 * 두었다.
 *
 * <p>{@code transactions} 테이블에 상대 은행 코드가 없어 이력의 {@code receiver_bank}는
 * 출금계좌의 은행 코드로 채운다. 2026-09-02 운영 AI 서버에 004·088·020 을 각각 넣어
 * 확인한 결과 점수가 소수점까지 완전히 같았다 — 이력의 은행 코드는 AI 가 쓰지 않는다.
 * 반대로 {@code receiver_account}·{@code amount}·{@code transaction_datetime}·
 * {@code medium}은 점수를 바꾸므로 실제 값이어야 한다.
 */
public record FraudDetectionRequest(
        @JsonProperty("current_transaction") TransactionData currentTransaction,
        List<TransactionData> history
) {

    public static FraudDetectionRequest of(
            final TransactionData currentTransaction,
            final List<TransactionData> history
    ) {
        if (history == null) {
            return new FraudDetectionRequest(currentTransaction, List.of());
        }
        return new FraudDetectionRequest(currentTransaction, List.copyOf(history));
    }
}
