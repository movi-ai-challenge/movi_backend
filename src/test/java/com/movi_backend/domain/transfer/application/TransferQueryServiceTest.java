package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.dto.response.TransferStatusResponse;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferQueryServiceTest {

    private static final Long USER_ID = 3L;

    @Mock private TransferRepository transferRepository;
    @Mock private FdsAssessmentRepository fdsAssessmentRepository;
    @Mock private Transfer transfer;
    @Mock private FdsAssessment assessment;

    @Test
    @DisplayName("완료된 이체 상태를 조회하면 위험도와 음성 안내를 반환한다")
    void 완료된_이체_상태를_조회하면_위험도와_음성_안내를_반환한다() {
        // given
        final String idempotencyKey = UUID.randomUUID().toString();
        given(transferRepository.findByIdempotencyKeyAndUserId(idempotencyKey, USER_ID))
                .willReturn(Optional.of(transfer));
        given(transfer.getId()).willReturn(101L);
        given(transfer.getStatus()).willReturn(TransferStatus.COMPLETED);
        given(transfer.getAmount()).willReturn(50_000L);
        given(transfer.getToHolderName()).willReturn("김영희");
        given(transfer.getRequestedAt()).willReturn(LocalDateTime.now().minusSeconds(2));
        given(transfer.getCompletedAt()).willReturn(LocalDateTime.now());
        given(fdsAssessmentRepository.findByTransferId(101L))
                .willReturn(Optional.of(assessment));
        given(assessment.getRiskLevel()).willReturn(RiskLevel.LOW);
        final TransferQueryService service = createService();

        // when
        final TransferStatusResponse response = service.findStatus(USER_ID, idempotencyKey);

        // then
        assertThat(response.transferId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.toVoiceMessage()).isEqualTo("김영희 님에게 5만원을 보냈어요.");
    }

    @Test
    @DisplayName("FDS 평가 중인 이체 상태를 조회하면 처리 중 안내를 반환한다")
    void FDS_평가_중인_이체_상태를_조회하면_처리_중_안내를_반환한다() {
        // given
        final String idempotencyKey = UUID.randomUUID().toString();
        given(transferRepository.findByIdempotencyKeyAndUserId(idempotencyKey, USER_ID))
                .willReturn(Optional.of(transfer));
        given(transfer.getId()).willReturn(102L);
        given(transfer.getStatus()).willReturn(TransferStatus.RISK_REVIEW);
        given(transfer.getAmount()).willReturn(50_000L);
        given(fdsAssessmentRepository.findByTransferId(102L)).willReturn(Optional.empty());
        final TransferQueryService service = createService();

        // when
        final TransferStatusResponse response = service.findStatus(USER_ID, idempotencyKey);

        // then
        assertThat(response.status()).isEqualTo(TransferStatus.RISK_REVIEW);
        assertThat(response.riskLevel()).isNull();
        assertThat(response.toVoiceMessage()).isEqualTo("송금을 안전하게 확인하고 있어요.");
    }

    @Test
    @DisplayName("다른 사용자의 멱등성 키를 조회하면 이체를 찾을 수 없는 예외가 발생한다")
    void 다른_사용자의_멱등성_키를_조회하면_이체를_찾을_수_없는_예외가_발생한다() {
        // given
        final String idempotencyKey = UUID.randomUUID().toString();
        given(transferRepository.findByIdempotencyKeyAndUserId(idempotencyKey, USER_ID))
                .willReturn(Optional.empty());
        final TransferQueryService service = createService();

        // when & then
        assertThatThrownBy(() -> service.findStatus(USER_ID, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSFER_NOT_FOUND);
        then(fdsAssessmentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 멱등성 키로 조회하면 요청 형식 예외가 발생한다")
    void 잘못된_멱등성_키로_조회하면_요청_형식_예외가_발생한다() {
        // given
        final TransferQueryService service = createService();

        // when & then
        assertThatThrownBy(() -> service.findStatus(USER_ID, "not-a-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(transferRepository).shouldHaveNoInteractions();
    }

    private TransferQueryService createService() {
        return new TransferQueryService(transferRepository, fdsAssessmentRepository);
    }
}
