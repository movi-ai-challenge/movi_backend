package com.movi_backend.domain.transfer.application.port;

import com.movi_backend.domain.transfer.entity.Transfer;

public interface TransferExecutionPort {

    void execute(Transfer transfer);
}
