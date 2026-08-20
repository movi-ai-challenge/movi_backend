package com.movi_backend.domain.transfer.infrastructure;

import com.movi_backend.domain.transfer.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.transfer.dto.OpenBankingTransferResult;

/**
 * 오픈뱅킹 게이트웨이.
 *
 * <p>Sandbox 승인 지연에 대비해 인터페이스를 먼저 고정하고 Mock으로 개발한다.
 * 실제 연동이 붙어도 이체 흐름 코드는 바뀌지 않는다.
 */
public interface OpenBankingClient {

    /** 출금 계좌의 현재 잔액. FDS의 {@code balanceBefore} 피처이자 잔액 부족 판정 근거다. */
    long inquireBalance(String fintechUseNum);

    /**
     * 출금이체를 실행한다.
     *
     * <p><b>고위험(HIGH/BLOCK) 판정에서는 이 메서드를 호출하지 않는다.</b>
     */
    OpenBankingTransferResult transfer(OpenBankingTransferCommand command);
}
