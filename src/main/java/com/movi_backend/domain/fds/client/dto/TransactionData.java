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
 *
 * <p>{@code transactionId}는 AI 가 여러 거래 중 어느 것이 "지금 평가할 거래"인지 찾는 데 쓴다.
 * <b>current_transaction 과 history 를 통틀어 겹치면 안 된다.</b> AI 가 중복을 발견하면 요청
 * 전체를 400 으로 거절한다 - 이력을 함께 보내기 시작하면서 실제로 걸렸다. 두 값이 서로 다른
 * 테이블({@code transfers}·{@code transactions})에서 오므로 숫자만 보내면 우연히 겹칠 수
 * 있어, 어느 쪽에서 온 값인지 접두어로 구분한다.
 */
public record TransactionData(
        @JsonProperty("transaction_id") String transactionId,
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
            final String transactionId,
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
                transactionId,
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
