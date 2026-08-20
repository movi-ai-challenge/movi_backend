package com.movi_backend.domain.account.application.port.dto;

import java.time.LocalDateTime;

/**
 * 사용자 인증으로 발급받은 토큰.
 *
 * <p>{@code userSeqNo}는 금결원이 부여하는 사용자 식별자이며, 이후 모든 계좌·이체 호출에
 * 함께 쓰인다. 토큰은 저장 시 AES 암호화 대상이고 로그에 남기지 않는다.
 *
 * @param accessToken  액세스 토큰
 * @param refreshToken 리프레시 토큰
 * @param userSeqNo    금결원 사용자일련번호
 * @param scope        발급된 권한 범위
 * @param expiresAt    만료 시각
 */
public record OpenBankingToken(
        String accessToken,
        String refreshToken,
        String userSeqNo,
        String scope,
        LocalDateTime expiresAt
) {
    public static OpenBankingToken of(
            final String accessToken,
            final String refreshToken,
            final String userSeqNo,
            final String scope,
            final LocalDateTime expiresAt
    ) {
        return new OpenBankingToken(accessToken, refreshToken, userSeqNo, scope, expiresAt);
    }
}
