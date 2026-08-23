package com.movi_backend.global.security;

import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String USER_TYPE_CLAIM = "user_type";
    private static final String TOKEN_VERSION_CLAIM = "token_version";
    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(final JwtProperties properties) {
        this.properties = properties;
        this.signingKey = createSigningKey(properties.secret());
    }

    public JwtTokenPair issueTokenPair(final AuthUser authUser) {
        final String accessToken = createUserToken(
                authUser,
                JwtTokenType.ACCESS,
                properties.accessTokenExpiration()
        );
        final String refreshToken = createUserToken(
                authUser,
                JwtTokenType.REFRESH,
                properties.refreshTokenExpiration()
        );
        return JwtTokenPair.of(
                accessToken,
                refreshToken,
                properties.accessTokenExpiration().toSeconds()
        );
    }

    public String issueOauthState() {
        final Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject("kakao-login")
                .claim(TOKEN_TYPE_CLAIM, JwtTokenType.OAUTH_STATE.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.oauthStateExpiration())))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    public void validateOauthState(final String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
        }
        final Claims claims = parse(state, ErrorCode.INVALID_OAUTH_STATE, ErrorCode.INVALID_OAUTH_STATE);
        validateTokenType(claims, JwtTokenType.OAUTH_STATE, ErrorCode.INVALID_OAUTH_STATE);
    }

    public AuthUser parseAccessToken(final String token) {
        final Claims claims = parse(
                token,
                ErrorCode.INVALID_ACCESS_TOKEN,
                ErrorCode.EXPIRED_ACCESS_TOKEN
        );
        validateTokenType(claims, JwtTokenType.ACCESS, ErrorCode.INVALID_ACCESS_TOKEN);
        return toAuthUser(claims, ErrorCode.INVALID_ACCESS_TOKEN);
    }

    public AuthUser parseRefreshToken(final String token) {
        final Claims claims = parse(
                token,
                ErrorCode.INVALID_REFRESH_TOKEN,
                ErrorCode.INVALID_REFRESH_TOKEN
        );
        validateTokenType(claims, JwtTokenType.REFRESH, ErrorCode.INVALID_REFRESH_TOKEN);
        return toAuthUser(claims, ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private String createUserToken(
            final AuthUser authUser,
            final JwtTokenType tokenType,
            final Duration expiration
    ) {
        final Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(authUser.userId()))
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .claim(USER_TYPE_CLAIM, authUser.userType().name())
                .claim(TOKEN_VERSION_CLAIM, authUser.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    private Claims parse(
            final String token,
            final ErrorCode invalidError,
            final ErrorCode expiredError
    ) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(invalidError);
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (final ExpiredJwtException exception) {
            throw new BusinessException(expiredError);
        } catch (final JwtException | IllegalArgumentException exception) {
            throw new BusinessException(invalidError);
        }
    }

    private void validateTokenType(
            final Claims claims,
            final JwtTokenType expectedType,
            final ErrorCode errorCode
    ) {
        final String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.name().equals(tokenType)) {
            throw new BusinessException(errorCode);
        }
    }

    private AuthUser toAuthUser(final Claims claims, final ErrorCode errorCode) {
        try {
            final Long userId = Long.valueOf(claims.getSubject());
            final UserType userType = UserType.valueOf(claims.get(USER_TYPE_CLAIM, String.class));
            final Number tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Number.class);
            if (tokenVersion == null) {
                throw new BusinessException(errorCode);
            }
            return AuthUser.of(userId, userType, tokenVersion.longValue());
        } catch (final RuntimeException exception) {
            throw new BusinessException(errorCode);
        }
    }

    private static SecretKey createSigningKey(final String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 서명 키 설정이 필요합니다.");
        }
        final byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("JWT 서명 키는 32바이트 이상이어야 합니다.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
