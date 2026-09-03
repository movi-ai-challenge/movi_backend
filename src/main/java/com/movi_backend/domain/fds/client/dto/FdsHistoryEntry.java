package com.movi_backend.domain.fds.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * FDS 평가에 함께 보내는 과거 출금 한 건.
 *
 * <p>AI 서버의 이상거래 규칙 중 {@code HIGH_AMOUNT_RATIO}처럼 <b>과거 대비 비율</b>을 보는
 * 것들은 이력이 없으면 아예 발동하지 않는다. 이력을 비워 보내면 금액이 얼마든 LOW 로만
 * 판정돼 위험탐지가 사실상 꺼진다 — 2026-09-02 운영 AI 서버로 확인했다(85만원·심야·음성
 * 거래가 이력 없이는 LOW 21.04, 30만원짜리 25건을 함께 보내면 MEDIUM 45.39).
 *
 * <p>{@code counterpartyAccountEncrypted}는 {@code transactions.counterparty_account}에
 * 저장된 암호문 그대로다. 복호화는 실제로 AI 로 보낼 때
 * ({@link com.movi_backend.domain.fds.client.HttpFdsAssessmentClient} 안)에서만 한다 —
 * {@link FdsAssessmentRequest#toAccountNumEncrypted()}와 같은 규칙이다. 현재 수취인이 이력에
 * 있으면 AI 가 {@code NEW_RECIPIENT}를 빼므로, 두 값의 복호화 방식이 어긋나면 재이체인데도
 * 매번 신규 수취인으로 잡힌다.
 *
 * <p>{@code transactionId}는 AI 가 현재 거래와 이력을 구분하는 데 쓴다
 * ({@link TransactionData} 참고).
 */
public record FdsHistoryEntry(
        Long transactionId,
        BigDecimal amount,
        OffsetDateTime occurredAt,
        String counterpartyAccountEncrypted
) {

    public FdsHistoryEntry {
        Objects.requireNonNull(transactionId, "transactionId는 필수입니다.");
        Objects.requireNonNull(amount, "amount는 필수입니다.");
        Objects.requireNonNull(occurredAt, "occurredAt은 필수입니다.");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
        if (counterpartyAccountEncrypted == null || counterpartyAccountEncrypted.isBlank()) {
            throw new IllegalArgumentException("counterpartyAccountEncrypted는 필수입니다.");
        }
    }

    public static FdsHistoryEntry of(
            final Long transactionId,
            final BigDecimal amount,
            final OffsetDateTime occurredAt,
            final String counterpartyAccountEncrypted
    ) {
        return new FdsHistoryEntry(
                transactionId, amount, occurredAt, counterpartyAccountEncrypted);
    }
}
