package com.movi_backend.domain.account.infrastructure.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.infrastructure.MockBalanceInquiryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 운영에서 실제로 쓰는 조합을 검증한다.
 *
 * <p>실제 잔액조회는 금융 사업자만 호출할 수 있어, 계좌 연결·목록은 실 API 로 쓰되
 * 잔액만 Mock 으로 둔다. {@code balance-mode} 를 적지 않았을 때 Mock 이 선택되는지도
 * 함께 본다 — 실 API 가 기본이면 운영에서 잔액조회가 조용히 실패한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.openbanking.mode=real",
        "movi.openbanking.base-url=https://testapi.openbanking.or.kr",
        "movi.openbanking.client-id=wiring-check",
        "movi.openbanking.client-secret=wiring-check",
        "movi.openbanking.client-use-code=M000000000"
})
@ActiveProfiles("test")
class OpenBankingMockBalanceWiringTest {

    @Autowired
    private OpenBankingClient openBankingClient;

    @Autowired
    private OpenBankingAuthClient openBankingAuthClient;

    @Autowired
    private BalanceInquiryPort balanceInquiryPort;

    @Test
    @DisplayName("계좌는 실 API 를 쓰면서 잔액조회만 Mock 으로 둘 수 있다")
    void 계좌는_실_API_잔액은_Mock_을_쓴다() {
        assertThat(openBankingClient).isInstanceOf(OpenBankingApiClient.class);
        assertThat(openBankingAuthClient).isInstanceOf(OpenBankingAuthApiClient.class);
        assertThat(balanceInquiryPort).isInstanceOf(MockBalanceInquiryAdapter.class);
    }

    @Test
    @DisplayName("mode 가 real 이어도 Mock 오픈뱅킹 어댑터 없이 잔액을 돌려준다")
    void Mock_어댑터_없이도_잔액을_돌려준다() {
        // mode=real 이라 MockOpenBankingClient 빈은 만들어지지 않는다.
        // 그래도 잔액조회가 예외 없이 값을 내야 한다.
        assertThat(balanceInquiryPort.inquire("120260215288981369293167", "any-token").balanceAmount())
                .isPositive();
    }
}
