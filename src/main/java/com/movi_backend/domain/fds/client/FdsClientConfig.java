package com.movi_backend.domain.fds.client;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "movi.fds", name = "client-type", havingValue = "http")
public class FdsClientConfig {

    @Bean("fdsRestClient")
    public RestClient fdsRestClient(
            final RestClient.Builder restClientBuilder,
            final FdsClientProperties properties
    ) {
        final SimpleClientHttpRequestFactory requestFactory = createRequestFactory(
                properties.connectTimeout(),
                properties.responseTimeout()
        );
        return restClientBuilder
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
