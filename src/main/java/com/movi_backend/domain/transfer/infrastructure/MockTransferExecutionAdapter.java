package com.movi_backend.domain.transfer.infrastructure;

import com.movi_backend.domain.transfer.application.port.TransferExecutionPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class MockTransferExecutionAdapter implements TransferExecutionPort {

    @Override
    public void execute(final Transfer transfer) {
        // 로컬·테스트에서는 실제 자금 이동 없이 성공 응답만 모사한다.
    }
}
