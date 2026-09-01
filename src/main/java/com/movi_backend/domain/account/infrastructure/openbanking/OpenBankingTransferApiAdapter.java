package com.movi_backend.domain.account.infrastructure.openbanking;

import com.movi_backend.domain.account.application.port.OpenBankingTransferPort;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 오픈뱅킹으로 출금이체를 보낸다.
 *
 * <p>HTTP 호출은 {@link OpenBankingApiClient}가 이미 갖고 있으므로 그대로 넘긴다.
 *
 * <p><b>이 어댑터는 {@code movi.openbanking.mode=real}을 함께 요구한다.</b> 그 빈이 없으면
 * 기동이 실패한다. Mock 인증으로 받은 토큰으로는 실제 이체를 보낼 수 없으니, 잘못 조합한
 * 설정으로 조용히 대역 이체를 실행하는 것보다 기동 시점에 멈추는 편이 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "movi.openbanking.transfer-mode", havingValue = "real")
public class OpenBankingTransferApiAdapter implements OpenBankingTransferPort {

    private final OpenBankingApiClient openBankingApiClient;

    @Override
    public OpenBankingTransferResult transfer(
            final OpenBankingTransferCommand command,
            final String accessToken
    ) {
        return openBankingApiClient.transfer(command, accessToken);
    }
}
