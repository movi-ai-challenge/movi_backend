package com.movi_backend.domain.fds.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 실제 AI FDS 서버({@code POST /api/v1/fraud/detect})가 받는 단일 거래.
 *
 * <p>필드명이 snake_case인 이유는 이 record가 나타내는 것이 우리 도메인이 아니라
 * <b>Python AI 서버의 계약</b>이기 때문이다. {@link FdsAssessmentRequest}(우리 내부 요청)와
 * 뒤섞지 않는다 — 어댑터 경계에서만 변환한다.
 *
 * <p>https://moviback.duckdns.org/ai/fds/openapi.json 을 그대로 옮긴 것이다.
 */
public record TransactionData(
        @JsonProperty("sender_account") String senderAccount,
        @JsonProperty("receiver_account") String receiverAccount,
        @JsonProperty("sender_bank") String senderBank,
        @JsonProperty("receiver_bank") String receiverBank,
        @JsonProperty("transaction_type") String transactionType,
        BigDecimal amount,
        @JsonProperty("transaction_datetime") OffsetDateTime transactionDatetime,
        String medium
) {

    public static TransactionData of(
            final String senderAccount,
            final String receiverAccount,
            final String senderBank,
            final String receiverBank,
            final String transactionType,
            final BigDecimal amount,
            final OffsetDateTime transactionDatetime,
            final String medium
    ) {
        return new TransactionData(
                senderAccount,
                receiverAccount,
                senderBank,
                receiverBank,
                transactionType,
                amount,
                transactionDatetime,
                medium
        );
    }
}
