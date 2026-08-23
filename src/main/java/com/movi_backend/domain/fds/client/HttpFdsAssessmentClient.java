package com.movi_backend.domain.fds.client;

import com.movi_backend.domain.fds.client.dto.FdsAssessmentRequest;
import com.movi_backend.domain.fds.client.dto.FdsAssessmentResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "movi.fds", name = "client-type", havingValue = "http")
public class HttpFdsAssessmentClient implements FdsAssessmentClient {

    private static final String ASSESSMENT_PATH = "/internal/v1/fraud/predict";

    private final RestClient restClient;
    private final FdsAssessmentResponseValidator responseValidator;

    public HttpFdsAssessmentClient(
            @Qualifier("fdsRestClient") final RestClient restClient,
            final FdsAssessmentResponseValidator responseValidator
    ) {
        this.restClient = restClient;
        this.responseValidator = responseValidator;
    }

    @Override
    public FdsAssessmentResponse assess(final FdsAssessmentRequest request) {
        try {
            final FdsAssessmentResponse response = restClient.post()
                    .uri(ASSESSMENT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FdsAssessmentResponse.class);
            return responseValidator.validate(request, response);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final ResourceAccessException exception) {
            if (containsTimeout(exception)) {
                throw assessmentTimeout(exception);
            }
            throw assessmentFailed(exception);
        } catch (final RestClientException exception) {
            if (isGatewayTimeout(exception)) {
                throw assessmentTimeout(exception);
            }
            throw assessmentFailed(exception);
        }
    }

    private boolean isGatewayTimeout(final RestClientException exception) {
        return exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT;
    }

    private boolean containsTimeout(final Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private BusinessException assessmentFailed(final Exception exception) {
        return new BusinessException(
                ErrorCode.ASSESSMENT_FAILED,
                exception.getClass().getSimpleName()
        );
    }

    private BusinessException assessmentTimeout(final Exception exception) {
        return new BusinessException(
                ErrorCode.ASSESSMENT_TIMEOUT,
                exception.getClass().getSimpleName()
        );
    }
}
