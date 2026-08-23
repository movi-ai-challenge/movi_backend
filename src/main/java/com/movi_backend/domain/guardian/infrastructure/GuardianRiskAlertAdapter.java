package com.movi_backend.domain.guardian.infrastructure;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.application.port.TransferRiskAlertPort;
import com.movi_backend.domain.transfer.entity.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class GuardianRiskAlertAdapter implements TransferRiskAlertPort {

    private final GuardianRiskAlertDeliveryService deliveryService;

    @Override
    public void send(final Transfer transfer, final FdsAssessment assessment) {
        final RiskLevel riskLevel = assessment.getRiskLevel();
        if (riskLevel != RiskLevel.MEDIUM && riskLevel != RiskLevel.HIGH) {
            return;
        }
        final Runnable delivery = () -> deliveryService.deliver(transfer.getId(), riskLevel);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            delivery.run();
                        }
                    }
            );
            return;
        }
        delivery.run();
    }
}
