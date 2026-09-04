package com.movi_backend.domain.account.infrastructure.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오픈뱅킹 실 API 어댑터의 HTTP 처리 검증.
 *
 * <p>테스트베드 인증정보 없이도 확인할 수 있는 것 — 요청 경로·헤더·본문 필드와,
 * <b>HTTP 200 으로 응답하면서 본문 {@code rsp_code} 로 실패를 알리는</b> 오픈뱅킹 특유의
 * 규약을 어댑터가 제대로 다루는지다. 이 분기를 놓치면 실패한 이체를 성공으로 처리한다.
 *
 * <p>실제 테스트베드 연동은 Sandbox 인증정보가 있어야 하며 이 테스트의 범위가 아니다.
 */
class OpenBankingApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("계좌 목록을 조회하면 인증 헤더를 싣고 res_list를 계좌로 변환한다")
    void 계좌_목록을_조회하면_인증_헤더를_싣고_res_list를_계좌로_변환한다() {
        // given
        respondWith(200, """
                {
                  "rsp_code": "A0000",
                  "res_list": [
                    {
                      "fintech_use_num": "199000000000000000000001",
                      "bank_code_std": "004",
                      "bank_name": "국민은행",
                      "account_num_masked": "1234***5678",
                      "account_holder_name": "김영희",
                      "account_type": "1"
                    }
                  ]
                }
                """);

        // when
        final List<OpenBankingAccount> accounts =
                createClient().fetchAccounts("U123456789", "access-token-abc");

        // then
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().fintechUseNum()).isEqualTo("199000000000000000000001");
        assertThat(accounts.getFirst().bankName()).isEqualTo("국민은행");
        assertThat(accounts.getFirst().accountType()).isEqualTo(AccountType.DEPOSIT);
        assertThat(lastPath.get()).contains("/v2.0/account/list");
        assertThat(lastPath.get()).contains("user_seq_no=U123456789");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer access-token-abc");
    }

    @Test
    @DisplayName("HTTP 200이어도 rsp_code가 정상이 아니면 계좌 조회를 실패로 처리한다")
    void HTTP_200이어도_rsp_code가_정상이_아니면_계좌_조회를_실패로_처리한다() {
        // given — 오픈뱅킹은 실패도 200으로 내려준다
        respondWith(200, """
                {"rsp_code": "A0001", "rsp_message": "사용자 인증 실패"}
                """);

        // expect
        assertThatThrownBy(() -> createClient().fetchAccounts("U123456789", "expired-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OPENBANK_COMMUNICATION_ERROR);
    }

    @Test
    @DisplayName("이체를 요청하면 거래고유번호와 금액을 본문에 싣고 결과를 반환한다")
    void 이체를_요청하면_거래고유번호와_금액을_본문에_싣고_결과를_반환한다() {
        // given
        respondWith(200, """
                {"rsp_code": "A0000", "bank_tran_id": "M000000000U0000000001"}
                """);

        // when
        final OpenBankingTransferResult result = createClient().transfer(
                OpenBankingTransferCommand.of(
                        "M000000000U0000000001",
                        "199000000000000000000001",
                        "088",
                        "110123456789",
                        "김영희",
                        50000L
                ),
                "access-token-abc"
        );

        // then
        assertThat(result.bankTranId()).isEqualTo("M000000000U0000000001");
        assertThat(lastPath.get()).contains("/v2.0/transfer/withdraw/fin_num");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer access-token-abc");
        assertThat(lastBody.get())
                .contains("\"bank_tran_id\":\"M000000000U0000000001\"")
                .contains("\"fintech_use_num\":\"199000000000000000000001\"")
                .contains("\"tran_amt\":\"50000\"")
                .contains("\"req_client_bank_code\":\"088\"");
    }

    @Test
    @DisplayName("HTTP 200이어도 rsp_code가 정상이 아니면 이체를 실패로 처리한다")
    void HTTP_200이어도_rsp_code가_정상이_아니면_이체를_실패로_처리한다() {
        // given
        respondWith(200, """
                {"rsp_code": "A0012", "rsp_message": "잔액 부족"}
                """);

        // expect
        assertThatThrownBy(() -> createClient().transfer(transferCommand(), "access-token-abc"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSFER_EXECUTION_FAILED);
    }

    @Test
    @DisplayName("오픈뱅킹이 5xx로 응답하면 이체 결과를 통신 오류로 남긴다")
    void 오픈뱅킹이_5xx로_응답하면_통신_오류로_변환한다() {
        // given
        respondWith(500, "{\"error\":\"internal\"}");

        // expect
        assertThatThrownBy(() -> createClient().transfer(transferCommand(), "access-token-abc"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OPENBANK_COMMUNICATION_ERROR);
    }

    @Test
    @DisplayName("잔액을 조회하면 거래고유번호를 실어 보내고 문자열 금액을 숫자로 변환한다")
    void 잔액을_조회하면_거래고유번호를_실어_보내고_문자열_금액을_숫자로_변환한다() {
        // given — 오픈뱅킹은 금액을 문자열로 준다
        respondWith(200, """
                {"rsp_code": "A0000", "balance_amt": "53000", "available_amt": "53000"}
                """);

        // when
        final var result = createBalanceClient()
                .inquire("199000000000000000000001", "access-token-abc");

        // then
        assertThat(result.balanceAmount()).isEqualTo(53000L);
        assertThat(lastPath.get()).contains("/v2.0/account/balance/fin_num");
        assertThat(lastPath.get()).contains("bank_tran_id=M000000000");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer access-token-abc");
    }

    @Test
    @DisplayName("HTTP 200이어도 rsp_code가 정상이 아니면 잔액조회를 실패로 처리한다")
    void HTTP_200이어도_rsp_code가_정상이_아니면_잔액조회를_실패로_처리한다() {
        // given
        respondWith(200, """
                {"rsp_code": "A0003", "rsp_message": "등록되지 않은 핀테크이용번호"}
                """);

        // expect
        assertThatThrownBy(() -> createBalanceClient().inquire("unknown", "access-token-abc"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_INQUIRY_FAILED);
    }

    private OpenBankingTransferCommand transferCommand() {
        return OpenBankingTransferCommand.of(
                "M000000000U0000000001",
                "199000000000000000000001",
                "088",
                "110123456789",
                "김영희",
                50000L
        );
    }

    private OpenBankingApiClient createClient() {
        return new OpenBankingApiClient(createProperties());
    }

    private OpenBankingBalanceApiClient createBalanceClient() {
        return new OpenBankingBalanceApiClient(createProperties(), new BankTranIdGenerator());
    }

    private OpenBankingProperties createProperties() {
        return new OpenBankingProperties(
                "real",
                "real",
                "real",
                baseUrl,
                "test-client-id",
                "test-client-secret",
                "http://localhost:8080/api/openbanking/callback",
                "http://localhost:3000/accounts/connect/callback",
                "login inquiry transfer",
                "M000000000"
        );
    }

    private void respondWith(final int statusCode, final String body) {
        server.createContext("/", exchange -> {
            capture(exchange);
            final byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
    }

    private void capture(final HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().toString());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        try (InputStream requestBody = exchange.getRequestBody()) {
            lastBody.set(new String(requestBody.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
