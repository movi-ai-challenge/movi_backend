package com.movi_backend.domain.notification.infrastructure.solapi;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "movi.sms", name = "provider", havingValue = "solapi")
public class SolapiClientConfig {

    @Bean("solapiRestClient")
    public RestClient solapiRestClient(
            final RestClient.Builder restClientBuilder,
            final SolapiProperties properties
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
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(responseTimeout);
        return requestFactory;
    }
}
