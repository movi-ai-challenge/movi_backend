package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.BalanceInquiryResult;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 잔액조회 실 API 어댑터.
 *
 * <p>오픈뱅킹은 HTTP 200 으로 응답하면서 본문의 {@code rsp_code} 로 실패를 알린다.
 * 상태 코드만 보면 실패를 성공으로 처리하게 되므로 본문을 검사한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "real")
public class OpenBankingBalanceApiClient implements BalanceInquiryPort {

    private static final String SUCCESS_CODE = "A0000";
    private static final String BALANCE_PATH = "/v2.0/account/balance/fin_num";
    private static final DateTimeFormatter TRAN_DTIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient restClient;
    private final OpenBankingProperties properties;
    private final BankTranIdGenerator bankTranIdGenerator;

    public OpenBankingBalanceApiClient(
            final OpenBankingProperties properties,
            final BankTranIdGenerator bankTranIdGenerator
    ) {
        this.properties = properties;
        this.bankTranIdGenerator = bankTranIdGenerator;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        log.info("잔액조회 실 API 어댑터 활성화");
    }

    @Override
    public BalanceInquiryResult inquire(final String fintechUseNum, final String accessToken) {
        final String uri = "%s?bank_tran_id=%s&fintech_use_num=%s&tran_dtime=%s".formatted(
                BALANCE_PATH,
                bankTranIdGenerator.generate(properties.clientUseCode()),
                fintechUseNum,
                LocalDateTime.now().format(TRAN_DTIME)
        );

        final Map<String, Object> body = call(uri, accessToken);
        return BalanceInquiryResult.of(
                amount(body, "balance_amt"),
                amount(body, "available_amt")
        );
    }

    private Map<String, Object> call(final String uri, final String accessToken) {
        try {
            final Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return verify(body);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            log.error("잔액조회 호출 실패", exception);
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED);
        }
    }

    private Map<String, Object> verify(final Map<String, Object> body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED, "empty response");
        }
        final Object code = body.get("rsp_code");
        if (!SUCCESS_CODE.equals(code)) {
            log.warn("잔액조회 오류 응답 rsp_code={} rsp_message={}", code, body.get("rsp_message"));
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED, "rsp_code=" + code);
        }
        return body;
    }

    /** 오픈뱅킹은 금액을 문자열로 준다. */
    private long amount(final Map<String, Object> body, final String key) {
        final Object value = body.get(key);
        if (value == null) {
            throw new BusinessException(ErrorCode.BALANCE_INQUIRY_FAILED, key + " 누락");
        }
        return Long.parseLong(String.valueOf(value).trim());
    }
}
