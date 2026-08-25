package com.movi_backend.domain.account.infrastructure.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.port.BalanceInquiryPort;
import com.movi_backend.domain.account.application.port.OpenBankingAuthClient;
import com.movi_backend.domain.account.application.port.OpenBankingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
class OpenBankingRealModeWiringTest {

    @Autowired
    private OpenBankingClient openBankingClient;

    @Autowired
    private OpenBankingAuthClient openBankingAuthClient;

    @Autowired
    private BalanceInquiryPort balanceInquiryPort;

    @Test
    @DisplayName("real 모드에서는 세 개의 실 API 어댑터가 모두 주입된다")
    void real_모드에서는_세_개의_실_API_어댑터가_모두_주입된다() {
        assertThat(openBankingClient).isInstanceOf(OpenBankingApiClient.class);
        assertThat(openBankingAuthClient).isInstanceOf(OpenBankingAuthApiClient.class);
        assertThat(balanceInquiryPort).isInstanceOf(OpenBankingBalanceApiClient.class);
    }
}
