package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.TransferConfirmation;
import com.movi_backend.domain.transfer.application.model.TransferExecutionResult;
import com.movi_backend.domain.transfer.dto.request.TransferExecuteRequest;
import com.movi_backend.domain.transfer.dto.request.TransferReviewRequest;
import com.movi_backend.domain.transfer.dto.response.TransferResultResponse;
import com.movi_backend.domain.transfer.dto.response.TransferReviewResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.SensitiveTextMasker;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면에서 직접 입력한 송금.
 *
 * <p>음성을 쓸 수 없는 상황(마이크 거부, 조용한 장소, 인식 반복 실패)에서도 송금을 끝낼 수
 * 있어야 한다. 접근성이 이 제품의 존재 이유인 만큼 음성은 <b>기본 경로이지 유일한 경로가
 * 아니다.</b>
 *
 * <p>대신 검증을 느슨하게 하지 않는다. 소유권·한도·잔액·FDS·멱등성은 음성과 같은
 * {@link TransferExecutionService}를 그대로 지난다. 이 클래스가 하는 일은 화면 입력을 검증된
 * 명령으로 바꿔 그 경로에 태우는 것뿐이다.
 *
 * <p>흐름은 두 단계다.
 *
 * <pre>
 * 검토(review) → 서버가 스냅샷 저장 + confirmationId 발급
 *              → 사용자가 화면에서 명시적으로 확인
 * 실행(execute) → confirmationId + 멱등성 키로 스냅샷 실행
 * </pre>
 *
 * <p>한 번의 요청으로 끝내지 않는 이유는 사용자가 <b>무엇이 나가는지 확인한 뒤에</b> 돈이
 * 움직여야 하기 때문이다. 음성 흐름의 확인 대기와 같은 규칙이다.
 */
@Service
@RequiredArgsConstructor
public class DirectTransferService {

    /**
     * 화면 입력은 음성 인식을 거치지 않으므로 오인식 위험이 없다.
     *
     * <p>FDS 계약의 {@code sttConfidence}는 "입력을 얼마나 믿을 수 있는가"를 뜻하므로 직접
     * 입력에는 1.0을 보낸다. 다만 모델 입장에서 음성과 화면 입력은 위험 성격이 다르므로,
     * 경로를 구분하는 피처는 AI 파트와 계약을 확정한 뒤 추가한다.
     */
    private static final BigDecimal DIRECT_INPUT_CONFIDENCE = BigDecimal.ONE;

    private final UserRepository userRepository;
    private final TransferTargetResolver transferTargetResolver;
    private final TransferValidationService transferValidationService;
    private final TransferConfirmationStore transferConfirmationStore;
    private final TransferExecutionService transferExecutionService;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /** 보낼 내용을 검증하고 확인 ID를 발급한다. 돈은 아직 움직이지 않는다. */
    @Transactional(readOnly = true)
    public TransferReviewResponse review(final Long userId, final TransferReviewRequest request) {
        final Account fromAccount = resolveFromAccount(userId, request.fromAccountId());
        final TransferRecipient recipient = transferTargetResolver.resolveOwnedRecipient(
                userId,
                request.recipientId()
        );
        transferValidationService.validateAmountRange(request.amount());

        final TransferConfirmation confirmation = transferConfirmationStore.issue(
                userId,
                fromAccount.getId(),
                recipient.getId(),
                request.amount(),
                LocalDateTime.now()
        );
        return TransferReviewResponse.of(
                confirmation,
                fromAccount,
                recipient.getHolderName(),
                recipient.getNickname(),
                maskAccountNum(recipient)
        );
    }

    /**
     * 확인된 송금을 실행한다.
     *
     * <p>순서가 중요하다. <b>멱등성 조회를 확인 검증보다 먼저</b> 한다. 응답을 받지 못한
     * 사용자가 같은 키로 재시도하면 이미 끝난 결과를 돌려줘야 하는데, 확인을 먼저 검사하면
     * 이미 소모된 확인 때문에 "확인 정보가 유효하지 않다"는 엉뚱한 오류가 난다.
     */
    @Transactional
    public TransferResultResponse execute(
            final Long userId,
            final TransferExecuteRequest request
    ) {
        final String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        final Optional<TransferExecutionResult> completed = transferExecutionService
                .findCompletedResult(userId, idempotencyKey);
        if (completed.isPresent()) {
            transferConfirmationStore.remove(request.confirmationId());
            return TransferResultResponse.from(completed.get());
        }

        final TransferConfirmation confirmation = transferConfirmationStore.bindIdempotencyKey(
                request.confirmationId(),
                userId,
                idempotencyKey,
                LocalDateTime.now()
        );
        if (confirmation == null) {
            throw new BusinessException(ErrorCode.CONFIRMATION_INVALID);
        }

        final TransferExecutionResult result = transferExecutionService.execute(
                createCommand(userId, confirmation, idempotencyKey)
        );
        if (result.status().isFinal()) {
            transferConfirmationStore.remove(confirmation.confirmationId());
        }
        return TransferResultResponse.from(result);
    }

    /**
     * 스냅샷의 계좌·수취인을 실행 시점에 다시 확인한다.
     *
     * <p>검토와 실행 사이에 계좌가 해지되거나 수취인이 지워질 수 있다. 스냅샷의 ID를 그대로
     * 믿고 실행하면 그 사이 사라진 대상으로 이체가 나간다.
     */
    private ConfirmedTransferCommand createCommand(
            final Long userId,
            final TransferConfirmation confirmation,
            final String idempotencyKey
    ) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        final Account fromAccount = transferTargetResolver.resolveOwnedAccount(
                userId,
                confirmation.fromAccountId()
        );
        final TransferRecipient recipient = transferTargetResolver.resolveOwnedRecipient(
                userId,
                confirmation.recipientId()
        );
        return ConfirmedTransferCommand.of(
                user,
                fromAccount,
                recipient,
                null,
                null,
                confirmation.amount(),
                idempotencyKey,
                DIRECT_INPUT_CONFIDENCE
        );
    }

    private Account resolveFromAccount(final Long userId, final Long fromAccountId) {
        if (fromAccountId == null) {
            return transferTargetResolver.resolveSourceAccount(userId, null);
        }
        return transferTargetResolver.resolveOwnedAccount(userId, fromAccountId);
    }

    private String normalizeIdempotencyKey(final String idempotencyKey) {
        final String normalizedKey = idempotencyKey.trim();
        try {
            UUID.fromString(normalizedKey);
        } catch (final IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "멱등성 키 형식 오류");
        }
        return normalizedKey;
    }

    private String maskAccountNum(final TransferRecipient recipient) {
        try {
            return SensitiveTextMasker.maskAccountNumber(
                    sensitiveDataCrypto.decrypt(recipient.getAccountNum())
            );
        } catch (final RuntimeException exception) {
            return "***";
        }
    }
}
