package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingToken;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 오픈뱅킹 사용자 인증(3-legged OAuth) 실 어댑터.
 *
 * <p>{@code redirectUri}는 <b>오픈뱅킹 포털에 등록한 값과 정확히 일치</b>해야 한다.
 * 한 글자만 달라도 인증이 거부된다. 로컬과 배포 서버 주소를 모두 등록하고 프로파일별로
 * 다른 값을 쓴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "real")
public class OpenBankingAuthApiClient implements OpenBankingAuthClient {

    private static final String AUTHORIZE_PATH = "/oauth/2.0/authorize";
    private static final String TOKEN_PATH = "/oauth/2.0/token";

    private final OpenBankingProperties properties;
    private final RestClient restClient;

    public OpenBankingAuthApiClient(final OpenBankingProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public String buildAuthorizationUrl(final String state) {
        return "%s%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s&auth_type=0"
                .formatted(
                        properties.baseUrl(),
                        AUTHORIZE_PATH,
                        encode(properties.clientId()),
                        encode(properties.redirectUri()),
                        encode(properties.scope()),
                        encode(state)
                );
    }

    @Override
    public OpenBankingToken exchangeCode(final String authorizationCode) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        return requestToken(form);
    }

    @Override
    public OpenBankingToken refresh(final String refreshToken) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("scope", properties.scope());
        form.add("grant_type", "refresh_token");
        return requestToken(form);
    }

    private OpenBankingToken requestToken(final MultiValueMap<String, String> form) {
        try {
            final Map<String, Object> body = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return toToken(body);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            log.error("오픈뱅킹 토큰 요청 실패", exception);
            throw new BusinessException(ErrorCode.OPENBANK_COMMUNICATION_ERROR);
        }
    }

    private OpenBankingToken toToken(final Map<String, Object> body) {
        if (body == null || body.get("access_token") == null) {
            throw new BusinessException(ErrorCode.OPENBANK_COMMUNICATION_ERROR, "no access_token");
        }
        return OpenBankingToken.of(
                String.valueOf(body.get("access_token")),
                text(body, "refresh_token"),
                text(body, "user_seq_no"),
                text(body, "scope"),
                LocalDateTime.now().plusSeconds(expiresIn(body))
        );
    }

    private long expiresIn(final Map<String, Object> body) {
        final Object value = body.get("expires_in");
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String text(final Map<String, Object> body, final String key) {
        final Object value = body.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String encode(final String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
