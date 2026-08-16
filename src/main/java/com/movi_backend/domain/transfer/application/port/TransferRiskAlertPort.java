package com.movi_backend.domain.transfer.application.port;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.transfer.entity.Transfer;

public interface TransferRiskAlertPort {

    void send(Transfer transfer, FdsAssessment assessment);
}
