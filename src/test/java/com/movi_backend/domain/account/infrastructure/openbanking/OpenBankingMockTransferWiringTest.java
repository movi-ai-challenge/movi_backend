package com.movi_backend.domain.account.infrastructure.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.OpenBankingClient;
import com.movi_backend.domain.account.application.port.OpenBankingTransferPort;
import com.movi_backend.domain.account.infrastructure.MockBalanceInquiryAdapter;
import com.movi_backend.domain.account.infrastructure.MockOpenBankingTransferAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 연결만 실제로 하고 돈은 움직이지 않는 조합을 검증한다.
 *
 * <p>오픈뱅킹 출금이체 API 는 사업자 등록을 마친 이용기관에만 열린다. 계좌 연결까지는
 * 샌드박스로 진행해도 이체는 보낼 수 없어, {@code mode=real} 이면서 이체·잔액만 대역인
 * 조합이 필요하다. {@code transfer-mode} 를 적지 않았을 때 Mock 이 선택되는지도 함께 본다 —
 * <b>실 API 가 기본이면 설정을 빠뜨린 환경에서 진짜 돈이 나간다.</b>
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
class OpenBankingMockTransferWiringTest {

    @Autowired
    private OpenBankingClient openBankingClient;

    @Autowired
    private OpenBankingTransferPort openBankingTransferPort;

    @Autowired
    private BalanceInquiryPort balanceInquiryPort;

    @Test
    @DisplayName("계좌 연결은 실 API 를 쓰면서 이체와 잔액만 Mock 으로 둘 수 있다")
    void 계좌_연결은_실_API_이체와_잔액은_Mock() {
        assertThat(openBankingClient).isInstanceOf(OpenBankingApiClient.class);
        assertThat(openBankingTransferPort).isInstanceOf(MockOpenBankingTransferAdapter.class);
        assertThat(balanceInquiryPort).isInstanceOf(MockBalanceInquiryAdapter.class);
    }

    @Test
    @DisplayName("transfer-mode 를 적지 않으면 Mock 이 선택된다")
    void transfer_mode_를_적지_않으면_Mock_이_선택된다() {
        // 실 API 가 기본이면 설정을 빠뜨린 환경에서 진짜 출금이 일어난다.
        assertThat(openBankingTransferPort).isInstanceOf(MockOpenBankingTransferAdapter.class);
    }
}
