package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.port.AccountHolderInquiryPort;
import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 현재 붙어 있는 예금주조회. 시연 환경에서는 {@link DemoAccountDirectory}가 은행 대신 답한다.
 *
 * <p><b>실제 모드에서는 아무것도 확인하지 못한다.</b> 오픈뱅킹 예금주조회는 아직 붙지 않았고,
 * {@link DemoAccountDirectory}는 {@code transfer-mode=mock} 에서만 빈으로 올라오기 때문이다.
 * 그때 이 어댑터는 빈 값을 돌려주고, 호출자는 이체를 진행하지 않는다.
 *
 * <p><b>확인할 수단이 없는 것을 확인된 것으로 다루지 않는다.</b> 그렇게 하면 검증 근거가
 * 사라진 채로 송금이 계속 나가고, 아무도 그 사실을 모른다. FDS 평가에 실패하면 이체를
 * 통과시키지 않는 것과 같은 규칙이다.
 *
 * <p>실제 예금주조회 어댑터가 생기면 이 클래스를 그것으로 교체한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountHolderInquiryAdapter implements AccountHolderInquiryPort {

    /** 실제 모드에서는 비어 있다. {@code MockIncomingTransactionRecorder} 와 같은 방식이다. */
    private final ObjectProvider<DemoAccountDirectory> demoAccountDirectory;

    @Override
    public Optional<VerifiedAccountHolder> inquire(
            final String bankCode,
            final String accountNumber
    ) {
        final DemoAccountDirectory directory = demoAccountDirectory.getIfAvailable();
        if (directory == null) {
            log.warn("[ACCOUNT] 예금주조회 수단이 없어 계좌를 확인하지 못했습니다. bankCode={}", bankCode);
            return Optional.empty();
        }
        return directory.find(bankCode, accountNumber)
                .map(account -> VerifiedAccountHolder.of(
                        account.bankCode(),
                        account.accountNumber(),
                        account.holderName()
                ));
    }
}
