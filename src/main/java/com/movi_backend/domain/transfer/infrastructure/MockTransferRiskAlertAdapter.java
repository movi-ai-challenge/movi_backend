package com.movi_backend.domain.transfer.infrastructure;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class MockTransferRiskAlertAdapter implements TransferRiskAlertPort {

    @Override
    public void send(final Transfer transfer, final FdsAssessment assessment) {
        // 보호자 알림 어댑터가 연결되기 전 로컬·테스트용 구현이다.
    }
}
