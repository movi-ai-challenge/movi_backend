package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 오픈뱅킹 실 API 어댑터.
 *
 * <p>{@code movi.openbanking.mode=real} 일 때만 활성화된다. 기본값은 Mock 이라,
 * 이 어댑터가 준비되지 않아도 팀원 개발은 막히지 않는다.
 *
 * <p><b>필드명은 오픈뱅킹 명세서 기준이다.</b> 테스트베드 호출로 검증하기 전까지는
 * 세부 필드가 명세와 어긋날 수 있으니, 실제 연동 시 응답을 로그로 확인하고 맞춘다.
 *
 * <p>오픈뱅킹은 HTTP 200 으로 응답하면서 본문의 {@code rsp_code} 로 실패를 알린다.
 * 그래서 상태 코드가 아니라 이 값을 봐야 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "movi.openbanking.mode", havingValue = "real")
public class OpenBankingApiClient implements OpenBankingClient {

    /** 오픈뱅킹 정상 응답 코드 */
    private static final String SUCCESS_CODE = "A0000";

    private static final DateTimeFormatter TRAN_DTIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 수시입출금 계좌 유형 코드 */
    private static final String ACCOUNT_TYPE_DEPOSIT = "1";

    private final RestClient restClient;

    public OpenBankingApiClient(final OpenBankingProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
        log.info("오픈뱅킹 실 API 어댑터 활성화 baseUrl={}", properties.baseUrl());
    }

    @Override
    public List<OpenBankingAccount> fetchAccounts(final String userSeqNo, final String accessToken) {
        final Map<String, Object> response = get(
                "/v2.0/account/list?user_seq_no=%s&include_cancel_yn=N&sort_order=D"
                        .formatted(userSeqNo),
                accessToken,
                ErrorCode.OPENBANK_COMMUNICATION_ERROR
        );

        final List<Map<String, Object>> accounts = asList(response.get("res_list"));
        return accounts.stream().map(this::toAccount).toList();
    }

    @Override
    public OpenBankingTransferResult transfer(
            final OpenBankingTransferCommand command,
            final String accessToken
    ) {
        final LocalDateTime now = LocalDateTime.now();
        final Map<String, Object> request = Map.of(
                "bank_tran_id", command.tranId(),
                "cntr_account_type", "N",
                "fintech_use_num", command.fromFintechUseNum(),
                "wd_print_content", "모비 송금",
                "tran_amt", String.valueOf(command.amount()),
                "tran_dtime", now.format(TRAN_DTIME),
                "req_client_name", command.toHolderName(),
                "req_client_bank_code", command.toBankCode(),
                "req_client_account_num", command.toAccountNum()
        );

        final Map<String, Object> response = post(
                "/v2.0/transfer/withdraw/fin_num",
                accessToken,
                request,
                ErrorCode.TRANSFER_EXECUTION_FAILED
        );

        return OpenBankingTransferResult.of(
                String.valueOf(response.get("bank_tran_id")),
                now,
                null
        );
    }

    private Map<String, Object> get(
            final String path,
            final String accessToken,
            final ErrorCode failureCode
    ) {
        try {
            final Map<String, Object> body = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .body(Map.class);
            return verify(body, failureCode);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            log.error("오픈뱅킹 호출 실패 path={}", path, exception);
            throw new BusinessException(failureCode);
        }
    }

    private Map<String, Object> post(
            final String path,
            final String accessToken,
            final Map<String, Object> request,
            final ErrorCode failureCode
    ) {
        try {
            final Map<String, Object> body = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return verify(body, failureCode);
        } catch (final BusinessException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            log.error("오픈뱅킹 호출 실패 path={}", path, exception);
            throw new BusinessException(failureCode);
        }
    }

    /** 오픈뱅킹은 200 으로 응답하면서 본문의 rsp_code 로 실패를 알린다. */
    private Map<String, Object> verify(final Map<String, Object> body, final ErrorCode failureCode) {
        if (body == null) {
            throw new BusinessException(failureCode, "empty response");
        }
        final Object code = body.get("rsp_code");
        if (!SUCCESS_CODE.equals(code)) {
            log.warn("오픈뱅킹 오류 응답 rsp_code={} rsp_message={}", code, body.get("rsp_message"));
            throw new BusinessException(failureCode, "rsp_code=" + code);
        }
        return body;
    }

    private OpenBankingAccount toAccount(final Map<String, Object> item) {
        return OpenBankingAccount.of(
                text(item, "fintech_use_num"),
                text(item, "bank_code_std"),
                text(item, "bank_name"),
                text(item, "account_num_masked"),
                text(item, "account_holder_name"),
                toAccountType(text(item, "account_type"))
        );
    }

    private AccountType toAccountType(final String code) {
        if (ACCOUNT_TYPE_DEPOSIT.equals(code)) {
            return AccountType.DEPOSIT;
        }
        return AccountType.SAVING;
    }

    private String bearer(final String accessToken) {
        return "Bearer " + accessToken;
    }

    private String text(final Map<String, Object> item, final String key) {
        final Object value = item.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(final Object value) {
        if (value == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) value;
    }
}
