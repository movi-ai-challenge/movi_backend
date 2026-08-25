package com.movi_backend.domain.fds.infrastructure;

import com.movi_backend.domain.fds.config.FdsProperties;
import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 파트 FDS 추론 API 호출.
 *
 * <p>{@code movi.fds.mode=http}일 때만 등록된다. 계약은 docs/ai-api-contract.md를 따른다.
 *
 * <p><b>자동 재시도를 하지 않는다.</b> 재시도 정책이 아직 명세에 없고, 임의로 재시도하면 사용자는
 * 응답 없이 계속 기다리게 된다. 실패는 곧바로 평가 실패로 올린다.
 *
 * <p>타임아웃과 통신 오류를 구분한다. 사용자에게 읽어 줄 문구가 다르기 때문이다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "movi.fds", name = "mode", havingValue = FdsProperties.MODE_HTTP)
public class HttpFdsClient implements FdsClient {

    private final FdsProperties fdsProperties;
    private final RestClient restClient;

    public HttpFdsClient(final FdsProperties fdsProperties) {
        this.fdsProperties = fdsProperties;
        this.restClient = RestClient.builder()
                .requestFactory(createRequestFactory(fdsProperties))
                .build();
    }

    @Override
    public FraudPredictResponse predict(final FraudPredictRequest request) {
        try {
            final FraudPredictResponse response = restClient.post()
                    .uri(fdsProperties.predictUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FraudPredictResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.ASSESSMENT_FAILED, "빈 응답");
            }
            return response;
        } catch (final ResourceAccessException exception) {
            log.warn("FDS 응답 지연 transferId={} type={}",
                    request.transferId(), exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.ASSESSMENT_TIMEOUT);
        } catch (final RestClientResponseException exception) {
            log.warn("FDS 오류 응답 transferId={} status={}",
                    request.transferId(), exception.getStatusCode().value());
            throw new BusinessException(ErrorCode.ASSESSMENT_FAILED);
        }
    }

    private static JdkClientHttpRequestFactory createRequestFactory(final FdsProperties properties) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
