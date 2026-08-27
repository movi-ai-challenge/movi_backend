package com.movi_backend.domain.fds.client;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * FDS 추론 API 호출용 {@link RestClient} 설정.
 *
 * <p><b>{@code RestClient.Builder} 빈을 주입받지 않는다.</b> 그 빈은
 * {@code spring-boot-restclient} 자동설정이 등록하는데 이 프로젝트에는
 * {@code spring-boot-webmvc}만 있어 존재하지 않는다. 주입받으면 이 설정이 켜지는
 * 순간(= {@code client-type: http}로 바꾸는 순간) 기동이 실패한다.
 * 카카오·오픈뱅킹 클라이언트와 같이 정적 팩토리로 만든다.
 */
@Configuration
@ConditionalOnProperty(prefix = "movi.fds", name = "client-type", havingValue = "http")
public class FdsClientConfig {

    @Bean("fdsRestClient")
    public RestClient fdsRestClient(final FdsClientProperties properties) {
        final SimpleClientHttpRequestFactory requestFactory = createRequestFactory(
                properties.connectTimeout(),
                properties.responseTimeout()
        );
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory(
            final Duration connectTimeout,
            final Duration responseTimeout
    ) {
        final SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(responseTimeout);
        return requestFactory;
    }
}
