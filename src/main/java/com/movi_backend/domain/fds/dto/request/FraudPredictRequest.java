package com.movi_backend.domain.fds.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FDS 예측 요청. docs/ai-api-contract.md 3.2의 스키마를 그대로 따른다.
 *
 * <p>{@code requestId}는 백엔드가 만들고 응답에서 같은 값이 돌아오는지 검증한다. 서로 다른 이체의
 * 평가 결과가 섞이면 엉뚱한 거래가 차단되거나 통과된다.
 */
public record FraudPredictRequest(
        String requestId,
        Long transferId,
        Long userId,
        Long amount,
        Long balanceBefore,
        LocalDateTime requestedAt,
        RecipientFeature recipient,
        ProfileFeature profile,
        ContextFeature context
) {

    /**
     * 수취인 이력.
     *
     * @param transferCount 이 수취인에게 보낸 횟수
     * @param firstTime     처음 보내는 상대인지 여부. {@code transferCount == 0}과 일치해야 한다
     */
    public record RecipientFeature(
            int transferCount,
            boolean firstTime
    ) {
        public static RecipientFeature of(final int transferCount) {
            return new RecipientFeature(transferCount, transferCount == 0);
        }

        /** 저장된 수취인이 아닌 경우. 처음 보내는 상대로 본다. */
        public static RecipientFeature unknown() {
            return new RecipientFeature(0, true);
        }
    }

    /**
     * 사용자 이체 행동 프로필.
     *
     * <p>{@code coldStart}이면 평균·최대·표준편차는 {@code null}, 횟수는 {@code 0},
     * {@code commonHours}는 빈 배열이다. 이력이 없는데 0을 평균으로 넘기면 모델이
     * "평소보다 무한대로 큰 금액"으로 읽는다.
     */
    public record ProfileFeature(
            boolean coldStart,
            Long averageAmount30d,
            Long maximumAmount30d,
            BigDecimal stddevAmount30d,
            int transferCount30d,
            int distinctRecipients30d,
            List<Integer> commonHours
    ) {
        /**
         * 이력이 없는 사용자의 피처.
         *
         * <p>이름을 {@code coldStart()}로 두면 record 컴포넌트 접근자와 시그니처가 겹친다.
         */
        public static ProfileFeature emptyHistory() {
            return new ProfileFeature(true, null, null, null, 0, 0, List.of());
        }

        public static ProfileFeature of(
                final Long averageAmount30d,
                final Long maximumAmount30d,
                final BigDecimal stddevAmount30d,
                final int transferCount30d,
                final int distinctRecipients30d,
                final List<Integer> commonHours
        ) {
            return new ProfileFeature(
                    false,
                    averageAmount30d,
                    maximumAmount30d,
                    stddevAmount30d,
                    transferCount30d,
                    distinctRecipients30d,
                    commonHours
            );
        }
    }

    /**
     * 요청 맥락.
     *
     * @param trustedDevice  등록된 기기에서 온 요청인지 여부
     * @param sttConfidence  음성 인식 신뢰도 0~1. 화면 조작으로 들어온 요청은 1.0
     */
    public record ContextFeature(
            boolean trustedDevice,
            double sttConfidence
    ) {
        public static ContextFeature of(final boolean trustedDevice, final double sttConfidence) {
            return new ContextFeature(trustedDevice, sttConfidence);
        }
    }
}
