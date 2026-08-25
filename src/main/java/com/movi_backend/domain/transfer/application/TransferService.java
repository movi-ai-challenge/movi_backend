package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.type.RiskLevel;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.PreparedTransfer;
import com.movi_backend.domain.transfer.dto.TransferConfirmation;
import com.movi_backend.domain.transfer.dto.response.TransferResponse;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.global.audit.application.AuditAction;
import com.movi_backend.global.audit.application.AuditLogService;
import com.movi_backend.global.audit.type.ActorType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이체 상태를 바꾸는 단위 작업.
 *
 * <p>메서드 하나가 트랜잭션 하나다. 오케스트레이션은 {@link TransferFacade}가 맡는다.
 * 이렇게 쪼개 두면 <b>FDS 판정과 상태 확정이 외부 호출 대기 시간에 묶이지 않고</b>,
 * 알림 발송 실패가 이미 확정된 상태를 되돌릴 수 없다.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final String BLOCK_REASON_USER_DECLINED = "USER_DECLINED";
    private static final String BLOCK_REASON_CONFIRMATION_EXPIRED = "CONFIRMATION_EXPIRED";

    private final TransferRepository transferRepository;
    private final TransferRecipientRepository transferRecipientRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final FdsAssessmentRepository fdsAssessmentRepository;
    private final AuditLogService auditLogService;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final TransferProperties transferProperties;

    /**
     * 같은 멱등성 키로 이미 처리된 이체를 찾는다.
     *
     * <p>있으면 오픈뱅킹을 다시 호출하지 않고 기존 결과를 그대로 돌려준다. 음성은 중복 발화가
     * 잦아서, 이 방어가 없으면 "보내줘"를 두 번 말한 사용자의 돈이 두 번 나간다.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResponse> findProcessed(final String idempotencyKey) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(transfer -> TransferResponse.from(transfer, findRiskLevel(transfer.getId())));
    }

    /** 이체 요청을 {@code PENDING}으로 저장한다. 아직 아무 돈도 움직이지 않는다. */
    @Transactional
    public PreparedTransfer create(final Long userId, final TransferCreateCommand command) {
        final User user = findActiveUser(userId);
        final Account fromAccount = findUsableAccount(command.fromAccountId(), userId);
        validateAmount(command.amount());

        final TransferRecipient recipient = findRecipientOrNull(command.recipientId(), userId);
        final ResolvedDestination destination = resolveDestination(command, recipient);

        final Transfer transfer = saveTransfer(user, fromAccount, recipient, destination, command);
        return toPrepared(transfer, fromAccount, destination.accountNum(), recipient);
    }

    /** FDS 평가 시작. {@code PENDING -> RISK_REVIEW} */
    @Transactional
    public void startRiskReview(final Long transferId) {
        findTransfer(transferId).startRiskReview();
    }

    /**
     * 고위험으로 감지해 본인 확인을 기다린다. {@code RISK_REVIEW -> HOLD}
     *
     * <p>이 시점에도 오픈뱅킹은 호출하지 않았다. 사용자가 "네"라고 답하기 전까지 돈은 그대로다.
     */
    @Transactional
    public TransferResponse hold(final Long transferId, final RiskLevel riskLevel) {
        final Transfer transfer = findTransfer(transferId);
        transfer.hold();
        auditLogService.record(
                transfer.getUser().getId(),
                ActorType.SYSTEM,
                AuditAction.TRANSFER_HELD,
                AuditAction.RESOURCE_TRANSFER,
                transferId
        );
        return TransferResponse.from(transfer, riskLevel);
    }

    /**
     * 재확인 요청을 처리할 수 있는 상태인지 확인하고, 진행에 필요한 값을 꺼낸다.
     *
     * <p>확인 시간이 지났으면 여기서 {@code BLOCKED}로 확정하고 만료를 알린다. 예외로 던지지
     * 않는 이유는 예외가 트랜잭션을 되돌려 그 확정을 지워 버리기 때문이다.
     *
     * <p>사용자가 확인했다는 사실은 감사 로그에 남긴다. 나중에 "나는 승낙한 적 없다"는 분쟁이
     * 생겼을 때 이 기록이 유일한 근거가 된다.
     */
    @Transactional
    public TransferConfirmation prepareConfirmation(final Long userId, final Long transferId) {
        final Transfer transfer = findOwnedTransfer(transferId, userId);
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            return TransferConfirmation.alreadyCompleted(
                    TransferResponse.from(transfer, findRiskLevel(transferId)));
        }
        if (!transfer.awaitsConfirmation()) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_AWAITING_CONFIRMATION);
        }
        if (isConfirmationExpired(transfer)) {
            transfer.block(BLOCK_REASON_CONFIRMATION_EXPIRED);
            auditLogService.record(userId, ActorType.SYSTEM, AuditAction.TRANSFER_BLOCKED,
                    AuditAction.RESOURCE_TRANSFER, transferId);
            return TransferConfirmation.expiredConfirmation();
        }

        auditLogService.record(userId, ActorType.USER, AuditAction.TRANSFER_CONFIRMED,
                AuditAction.RESOURCE_TRANSFER, transferId);
        return TransferConfirmation.ready(toPrepared(
                transfer,
                transfer.getFromAccount(),
                sensitiveDataCrypto.decrypt(transfer.getToAccountNum()),
                transfer.getRecipient()
        ));
    }

    /** 사용자가 "아니요"라고 답했다. {@code HOLD -> BLOCKED} */
    @Transactional
    public TransferResponse decline(final Long userId, final Long transferId) {
        final Transfer transfer = findOwnedTransfer(transferId, userId);
        if (!transfer.awaitsConfirmation()) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_AWAITING_CONFIRMATION);
        }
        transfer.block(BLOCK_REASON_USER_DECLINED);
        auditLogService.record(userId, ActorType.USER, AuditAction.TRANSFER_DECLINED,
                AuditAction.RESOURCE_TRANSFER, transferId);
        return TransferResponse.from(transfer, findRiskLevel(transferId));
    }

    /**
     * 이체를 완료 처리한다.
     *
     * <p><b>오픈뱅킹 성공 응답을 받은 뒤에만 호출한다.</b> 상태를 먼저 바꾸고 외부를 호출하면
     * 실패했을 때 "보냈다"고 안내한 뒤 되돌려야 한다.
     */
    @Transactional
    public TransferResponse complete(
            final Long transferId,
            final RiskLevel riskLevel,
            final LocalDateTime now
    ) {
        final Transfer transfer = findTransfer(transferId);
        transfer.complete(now);
        recordRecipientTransfer(transfer, now);
        auditLogService.record(
                transfer.getUser().getId(),
                ActorType.USER,
                AuditAction.TRANSFER_COMPLETED,
                AuditAction.RESOURCE_TRANSFER,
                transferId
        );
        return TransferResponse.from(transfer, riskLevel);
    }

    /**
     * 이체를 차단한다.
     *
     * <p>이 트랜잭션이 커밋되면 금융 상태는 확정이다. 이후 보호자 알림이 실패해도
     * {@code BLOCKED}는 유지된다.
     */
    @Transactional
    public TransferResponse block(
            final Long transferId,
            final RiskLevel riskLevel,
            final String reason
    ) {
        final Transfer transfer = findTransfer(transferId);
        transfer.block(reason);
        auditLogService.record(
                transfer.getUser().getId(),
                ActorType.SYSTEM,
                AuditAction.TRANSFER_BLOCKED,
                AuditAction.RESOURCE_TRANSFER,
                transferId
        );
        return TransferResponse.from(transfer, riskLevel);
    }

    /** 외부 연동 실패·잔액 부족 등으로 실패 처리한다. */
    @Transactional
    public void fail(final Long transferId, final String reason) {
        findTransfer(transferId).fail(reason);
    }

    @Transactional(readOnly = true)
    public RiskLevel findRiskLevel(final Long transferId) {
        return fdsAssessmentRepository.findByTransferId(transferId)
                .map(FdsAssessment::getRiskLevel)
                .orElse(null);
    }

    /**
     * 확인 대기 시간이 지났는지 본다.
     *
     * <p>만료 시각을 따로 저장하지 않고 {@code requested_at}에서 계산한다. 설정을 바꾸면 아직
     * 대기 중인 건에도 즉시 반영되고, 컬럼을 늘리지 않아도 된다.
     */
    private boolean isConfirmationExpired(final Transfer transfer) {
        final LocalDateTime deadline = transfer.getRequestedAt()
                .plusMinutes(transferProperties.confirmationExpireMinutes());
        return LocalDateTime.now().isAfter(deadline);
    }

    private PreparedTransfer toPrepared(
            final Transfer transfer,
            final Account fromAccount,
            final String plainToAccountNum,
            final TransferRecipient recipient
    ) {
        return new PreparedTransfer(
                transfer.getId(),
                transfer.getUser().getId(),
                transfer.getAmount(),
                transfer.getRequestedAt(),
                fromAccount.getFintechUseNum(),
                transfer.getToBankCode(),
                plainToAccountNum,
                transfer.getToHolderName(),
                recipientTransferCountOrNull(recipient)
        );
    }

    private Transfer saveTransfer(
            final User user,
            final Account fromAccount,
            final TransferRecipient recipient,
            final ResolvedDestination destination,
            final TransferCreateCommand command
    ) {
        final Transfer transfer = Transfer.builder()
                .user(user)
                .fromAccount(fromAccount)
                .recipient(recipient)
                .toBankCode(destination.bankCode())
                .toAccountNum(sensitiveDataCrypto.encrypt(destination.accountNum()))
                .toHolderName(destination.holderName())
                .amount(command.amount())
                .idempotencyKey(command.idempotencyKey())
                .build();
        try {
            return transferRepository.saveAndFlush(transfer);
        } catch (final DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRANSFER);
        }
    }

    private void recordRecipientTransfer(final Transfer transfer, final LocalDateTime now) {
        final TransferRecipient recipient = transfer.getRecipient();
        if (recipient == null) {
            return;
        }
        recipient.recordTransfer(now);
    }

    /**
     * 저장된 수취인이면 저장된 계좌를, 아니면 요청에 담긴 계좌를 쓴다.
     *
     * <p>저장된 수취인의 계좌번호는 암호화돼 있어 오픈뱅킹 호출 직전에 복호화한다.
     */
    private ResolvedDestination resolveDestination(
            final TransferCreateCommand command,
            final TransferRecipient recipient
    ) {
        if (recipient != null) {
            return new ResolvedDestination(
                    recipient.getBankCode(),
                    sensitiveDataCrypto.decrypt(recipient.getAccountNum()),
                    recipient.getHolderName()
            );
        }
        if (!command.hasDirectAccount()) {
            throw new BusinessException(ErrorCode.RECIPIENT_MISSING);
        }
        return new ResolvedDestination(
                command.toBankCode(),
                command.toAccountNum(),
                command.toHolderName()
        );
    }

    private TransferRecipient findRecipientOrNull(final Long recipientId, final Long userId) {
        if (recipientId == null) {
            return null;
        }
        final TransferRecipient recipient = transferRecipientRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!recipient.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return recipient;
    }

    private Integer recipientTransferCountOrNull(final TransferRecipient recipient) {
        if (recipient == null) {
            return null;
        }
        return recipient.getTransferCount();
    }

    private Account findUsableAccount(final Long accountId, final Long userId) {
        final Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
    }

    private void validateAmount(final Long amount) {
        if (amount == null || amount <= 0L) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
    }

    private Transfer findTransfer(final Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
    }

    /** 남의 이체를 확인·거절할 수 없다. 조회 단계에서 소유자를 함께 건다. */
    private Transfer findOwnedTransfer(final Long transferId, final Long userId) {
        return transferRepository.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
    }

    private User findActiveUser(final Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 최종 확정된 수취 계좌 정보 */
    private record ResolvedDestination(
            String bankCode,
            String accountNum,
            String holderName
    ) {
    }
}
