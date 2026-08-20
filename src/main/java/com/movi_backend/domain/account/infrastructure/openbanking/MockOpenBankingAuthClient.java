package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingToken;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 사용자 인증 Mock 어댑터.
 *
 * <p>오픈뱅킹 포털에 Callback URL을 등록하기 전에도 계좌 연결 흐름을 개발·시연할 수 있게 한다.
 * 인증 페이지 대신 우리 콜백으로 바로 돌아오는 URL을 돌려주므로, 브라우저 리다이렉트만으로
 * 전체 흐름을 태울 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "mock", matchIfMissing = true)
public class MockOpenBankingAuthClient implements OpenBankingAuthClient {

    private static final String MOCK_USER_SEQ_NO = "U001";
    private static final long TOKEN_VALID_DAYS = 90L;

    private final OpenBankingProperties properties;
    private final AtomicLong tokenSequence = new AtomicLong(1L);

    public MockOpenBankingAuthClient(final OpenBankingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String buildAuthorizationUrl(final String state) {
        log.info("[MOCK] 인증 URL 생성 — 실제 오픈뱅킹 대신 콜백으로 바로 돌아온다");
        return "%s?code=mock-authorization-code&state=%s".formatted(properties.redirectUri(), state);
    }

    @Override
    public OpenBankingToken exchangeCode(final String authorizationCode) {
        log.info("[MOCK] 인가 코드 교환");
        return issueToken();
    }

    @Override
    public OpenBankingToken refresh(final String refreshToken) {
        log.info("[MOCK] 토큰 갱신");
        return issueToken();
    }

    private OpenBankingToken issueToken() {
        final long sequence = tokenSequence.getAndIncrement();
        return OpenBankingToken.of(
                "mock-access-token-%d".formatted(sequence),
                "mock-refresh-token-%d".formatted(sequence),
                MOCK_USER_SEQ_NO,
                properties.scope(),
                LocalDateTime.now().plusDays(TOKEN_VALID_DAYS)
        );
    }
}
