package com.movi_backend.domain.guardian.infrastructure;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.entity.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class GuardianRiskAlertAdapterTest {

    @Mock private GuardianRiskAlertDeliveryService deliveryService;
    @Mock private Transfer transfer;
    @Mock private FdsAssessment assessment;

    @InjectMocks
    private GuardianRiskAlertAdapter adapter;

    @Test
    @DisplayName("송금 트랜잭션이 있으면 커밋 전에는 알림을 발송하지 않는다")
    void 송금_트랜잭션이_있으면_커밋_후_알림을_발송한다() {
        given(assessment.getRiskLevel()).willReturn(RiskLevel.MEDIUM);
        given(transfer.getId()).willReturn(101L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        try {
            adapter.send(transfer, assessment);
            then(deliveryService).shouldHaveNoInteractions();

            for (final TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            then(deliveryService).should().deliver(101L, RiskLevel.MEDIUM);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 위험 알림 전달을 즉시 시작한다")
    void 트랜잭션이_없으면_위험_알림_전달을_즉시_시작한다() {
        given(assessment.getRiskLevel()).willReturn(RiskLevel.HIGH);
        given(transfer.getId()).willReturn(101L);

        adapter.send(transfer, assessment);

        then(deliveryService).should().deliver(101L, RiskLevel.HIGH);
    }

    @Test
    @DisplayName("저위험 이체는 보호자 알림 대상이 아니다")
    void 저위험_이체는_보호자_알림_대상이_아니다() {
        given(assessment.getRiskLevel()).willReturn(RiskLevel.LOW);

        adapter.send(transfer, assessment);

        then(deliveryService).shouldHaveNoInteractions();
    }
}
