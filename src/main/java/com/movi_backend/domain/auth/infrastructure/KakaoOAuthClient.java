package com.movi_backend.domain.auth.infrastructure;

import com.movi_backend.domain.auth.config.KakaoProperties;
import com.movi_backend.domain.auth.dto.response.KakaoTokenResponse;
import com.movi_backend.domain.auth.dto.response.KakaoUserInfo;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient {

    private static final String AUTHORIZATION_CODE = "authorization_code";

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(final KakaoProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public URI buildAuthorizationUri(final String state) {
        return UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("client_id", required(properties.restApiKey(), "카카오 REST API 키"))
                .queryParam("redirect_uri", required(properties.redirectUri(), "카카오 Redirect URI"))
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    public KakaoTokenResponse requestToken(final String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_AUTHORIZATION_FAILED);
        }

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", AUTHORIZATION_CODE);
        formData.add("client_id", required(properties.restApiKey(), "카카오 REST API 키"));
        formData.add("redirect_uri", required(properties.redirectUri(), "카카오 Redirect URI"));
        formData.add("code", authorizationCode);
        if (properties.hasClientSecret()) {
            formData.add("client_secret", properties.clientSecret());
        }

        try {
            final KakaoTokenResponse response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(ErrorCode.KAKAO_TOKEN_IS_BLANK);
            }
            return response;
        } catch (final RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.KAKAO_AUTHORIZATION_FAILED,
                    "카카오 토큰 요청 응답 상태=" + exception.getStatusCode().value()
            );
        } catch (final ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.KAKAO_COMMUNICATION_ERROR);
        }
    }

    public KakaoUserInfo requestUserInfo(final String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_IS_BLANK);
        }
        try {
            final KakaoUserInfo userInfo = restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUserInfo.class);
            if (userInfo == null || userInfo.providerUserId() == null) {
                throw new BusinessException(ErrorCode.KAKAO_COMMUNICATION_ERROR);
            }
            return userInfo;
        } catch (final RestClientResponseException | ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.KAKAO_COMMUNICATION_ERROR);
        }
    }

    private static String required(final String value, final String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " 설정이 필요합니다.");
        }
        return Objects.requireNonNull(value);
    }
}
