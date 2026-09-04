package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.application.model.ConfirmedTransferCommand;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
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
    private final DeviceRegistrationService deviceRegistrationService;
    private final TransferTargetResolver transferTargetResolver;
    private final TransferTargetVerifier transferTargetVerifier;
    private final TransferRecipientRegistrar transferRecipientRegistrar;
    private final BankDirectory bankDirectory;
    private final TransferValidationService transferValidationService;
    private final TransferConfirmationStore transferConfirmationStore;
    private final TransferExecutionService transferExecutionService;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 보낼 내용을 검증하고 확인 ID를 발급한다. 돈은 아직 움직이지 않는다.
     *
     * <p>확인 스냅샷을 새로 발급하면 <b>같은 사용자의 이전 확인은 버려진다</b>
     * ({@link TransferConfirmationStore}). 대상이나 금액을 고쳐 다시 검토했는데 앞의 확인이
     * 살아 있으면, 사용자가 고치기 전 내용이 그대로 나갈 수 있다.
     */
    @Transactional
    public TransferReviewResponse review(final Long userId, final TransferReviewRequest request) {
        final Account fromAccount = resolveFromAccount(userId, request.fromAccountId());
        final TransferRecipient recipient = resolveReviewRecipient(userId, request);
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
                bankDirectory.displayNameOf(recipient.getBankCode()),
                maskAccountNum(recipient)
        );
    }

    /**
     * 검토할 수취인을 정한다.
     *
     * <p>주소록에서 고른 경우와 계좌번호를 직접 넣은 경우를 모두 받는다. 어느 쪽이든
     * <b>예금주조회로 확인된 계좌</b>여야 하고, 확인되지 않으면 검토가 끝나지 않는다.
     */
    private TransferRecipient resolveReviewRecipient(
            final Long userId,
            final TransferReviewRequest request
    ) {
        if (request.hasRegisteredRecipient() && request.hasOneTimeAccount()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "받는 분과 계좌번호 중 하나만 보내 주세요."
            );
        }
        if (request.hasOneTimeAccount()) {
            return resolveOneTimeRecipient(userId, request);
        }
        if (!request.hasRegisteredRecipient()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "받는 분을 지정해 주세요.");
        }
        return requireVerified(
                transferTargetResolver.resolveOwnedRecipient(userId, request.recipientId())
        );
    }

    /** 등록하지 않은 계좌. 확인한 뒤 주소록이 아닌 거래 상대 신원 행으로 남긴다. */
    private TransferRecipient resolveOneTimeRecipient(
            final Long userId,
            final TransferReviewRequest request
    ) {
        final VerifiedTransferTarget target = transferTargetVerifier.verifyForTransfer(
                userId,
                request.bankCode(),
                request.accountNumber()
        );
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return transferRecipientRegistrar.resolveTransferTarget(
                user,
                target,
                LocalDateTime.now()
        );
    }

    /**
     * 검증 없이 저장되던 시절의 수취인으로는 보내지 않는다.
     *
     * <p>은행에 다시 물어 확인되면 그 사실을 남기고 진행한다. 확인되지 않으면 안내하고
     * 멈춘다 — 별칭 모양이나 이체 횟수로는 그 계좌가 맞는지 알 수 없다.
     */
    private TransferRecipient requireVerified(final TransferRecipient recipient) {
        if (recipient.isVerified()) {
            return recipient;
        }
        final Optional<VerifiedAccountHolder> holder = transferTargetVerifier.reverify(
                recipient.getBankCode(),
                decryptOrNull(recipient.getAccountNum())
        );
        if (holder.isEmpty()) {
            throw new BusinessException(ErrorCode.RECIPIENT_UNVERIFIED);
        }
        recipient.verify(holder.get().holderName(), LocalDateTime.now());
        return recipient;
    }

    private String decryptOrNull(final String encrypted) {
        try {
            return sensitiveDataCrypto.decrypt(encrypted);
        } catch (final RuntimeException exception) {
            return null;
        }
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
                createCommand(userId, confirmation, idempotencyKey, request.deviceUuid())
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
            final String idempotencyKey,
            final String deviceUuid
    ) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        final Account fromAccount = transferTargetResolver.resolveOwnedAccount(
                userId,
                confirmation.fromAccountId()
        );
        final TransferRecipient recipient = requireVerified(
                transferTargetResolver.resolveOwnedRecipient(userId, confirmation.recipientId())
        );
        final Device device = deviceRegistrationService.findOwnedDevice(userId, deviceUuid);
        return ConfirmedTransferCommand.of(
                user,
                fromAccount,
                recipient,
                null,
                device,
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
