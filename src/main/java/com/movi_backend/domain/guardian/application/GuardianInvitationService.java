package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.guardian.config.GuardianProperties;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 초대 토큰 생성과 초대 링크 조립.
 *
 * <p>토큰은 이 링크를 가진 사람이 곧 보호자로 인정되는 자격 증명이다. 그래서 {@code linkId},
 * {@code userId}, 전화번호, 현재 시각처럼 <b>추측 가능한 값에서 파생시키지 않는다.</b>
 * 256비트 난수를 그대로 쓴다.
 *
 * <p>생성된 토큰을 로그·예외 메시지·감사 detail에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GuardianInvitationService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String TOKEN_QUERY_FORMAT = "%s?token=%s";

    private final GuardianProperties guardianProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** URL-safe Base64 토큰. 32바이트 → 43자로 {@code invite_token VARCHAR(64)} 안에 들어간다. */
    public String generateInviteToken() {
        final byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /** 만료 시각. 유효 시간은 설정값이며 코드에 하드코딩하지 않는다. */
    public LocalDateTime calculateExpiresAt(final LocalDateTime now) {
        return now.plusHours(guardianProperties.invitationExpireHours());
    }

    public String buildInviteUrl(final String inviteToken) {
        return TOKEN_QUERY_FORMAT.formatted(guardianProperties.inviteUrl(), inviteToken);
    }
}
