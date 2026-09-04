package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발화에서 뽑아 온 이체 명령을 <b>실행 가능한 대상</b>으로 바꾼다.
 *
 * <p>대상을 정하는 방법은 둘뿐이다.
 *
 * <ul>
 *   <li><b>주소록 이름</b> — 사용자가 직접 등록해 둔 이름과 정확히 같아야 한다</li>
 *   <li><b>은행 + 전체 계좌번호</b> — 예금주조회로 확인된 계좌여야 한다</li>
 * </ul>
 *
 * <p><b>추측하지 않는다.</b> 예전에는 자모 편집거리 1 이내면 가장 가까운 이름을 골랐는데,
 * 자동 생성 별칭 "국민은행 6789"와 "국민은행 6788"이 거리 1이라 한 자리 다른 계좌가 유일
 * 후보로 선택될 수 있었다. 짧은 사람 이름도 마찬가지다("형"과 "혁"). 잘못 고르는 것보다
 * 다시 묻는 편이 안전하다.
 *
 * <p><b>주소록을 늘리지 않는다.</b> 계좌번호로 보낼 때 만드는 행은 거래 상대의 신원일 뿐
 * 주소록 항목이 아니다({@link TransferRecipientRegistrar}). 이름은 사용자가 등록 흐름에서
 * 직접 지을 때만 붙는다.
 */
@Service
@RequiredArgsConstructor
public class TransferValidationService {

    private static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.80");
    private static final BigDecimal MAXIMUM_CONFIDENCE = BigDecimal.ONE;
    private static final String RECIPIENT_QUESTION = "누구에게 보내시겠어요?";
    private static final String AMOUNT_QUESTION = "얼마를 보내시겠어요?";

    /*
     * 재질문 문구는 상황마다 하나로 고정한다. 같은 상황에서 매번 다른 말이 나오면 화면을
     * 보지 않는 사용자가 무엇이 바뀐 것인지 몰라 혼란스럽다(CLAUDE.md 도메인 규칙 1).
     */
    private static final String BANK_QUESTION =
            "어느 은행 계좌인가요? 은행 이름을 말씀해 주세요.";
    private static final String ACCOUNT_NUMBER_QUESTION =
            "계좌번호를 말씀해 주세요.";
    private static final String UNKNOWN_RECIPIENT_QUESTION =
            "%s 님은 저장돼 있지 않아요. 받는 분의 은행과 계좌번호를 말씀해 주세요.";

    /**
     * 이름과 계좌가 서로 다른 상대를 가리킬 때.
     *
     * <p>어느 쪽도 고르지 않는다. 저장된 상대에게 보내려던 것인지 새 계좌로 보내려던
     * 것인지는 사용자만 안다. 임의로 골라 보내면 되돌릴 수 없다.
     */
    private static final String RECIPIENT_ACCOUNT_CONFLICT_QUESTION =
            "%s 님으로 저장된 계좌와 다른 계좌예요. 저장된 분께 보내시려면 이름만, "
                    + "새 계좌로 보내시려면 은행과 계좌번호만 말씀해 주세요.";

    private final TransferRecipientRepository transferRecipientRepository;
    private final TransferProperties transferProperties;
    private final UserRepository userRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final TransferTargetVerifier transferTargetVerifier;
    private final TransferRecipientRegistrar transferRecipientRegistrar;

    /**
     * 계좌번호로 보낼 때 거래 상대의 신원 행을 남기므로 읽기 전용이 아니다.
     *
     * <p>이체 실행과 FDS 수취인 피처(재이체 횟수·첫 거래 여부)가 그 행에 달려 있다. 다만
     * <b>주소록에는 올리지 않는다</b> — 확인을 취소해도 사용자의 수취인 목록은 그대로다.
     */
    @Transactional
    public TransferValidationResult validate(
            final Long userId,
            final TransferCommandRequest command
    ) {
        validateOverallConfidence(command);

        final List<TransferSlot> missingSlots = findMissingSlots(command);
        if (!missingSlots.isEmpty()) {
            return createClarification(command, missingSlots);
        }

        validateAmountRange(command.amount());

        final Optional<TransferRecipient> namedRecipient = findAddressBookEntry(userId, command);
        if (command.hasSpokenAccount()) {
            return resolveBySpokenAccount(userId, command, namedRecipient);
        }
        return resolveByName(command, namedRecipient);
    }

    /**
     * 은행과 계좌번호를 말한 경우.
     *
     * <p>주소록에 등록돼 있지 않아도 보낼 수 있다. 대신 <b>예금주조회로 확인한 뒤에만</b>
     * 진행하고, 확인 단계에서 계좌번호를 한 자리씩 읽어 준다.
     */
    private TransferValidationResult resolveBySpokenAccount(
            final Long userId,
            final TransferCommandRequest command,
            final Optional<TransferRecipient> namedRecipient
    ) {
        final VerifiedTransferTarget target = transferTargetVerifier.verifyForTransfer(
                userId,
                command.bankCode(),
                command.accountNumber()
        );

        /*
         * 이름은 별칭일 수도 예금주명일 수도 있다. 저장된 별칭과 다른 계좌를 함께 말한
         * 경우에만 되묻는다 — 예금주명을 말한 것이라면 저장된 별칭에 걸리지 않는다.
         */
        if (namedRecipient.isPresent() && !pointsToSameAccount(namedRecipient.get(), target)) {
            return TransferClarification.of(
                    List.of(TransferSlot.RECIPIENT),
                    RECIPIENT_ACCOUNT_CONFLICT_QUESTION.formatted(command.recipient().trim())
            );
        }

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        final TransferRecipient recipient = transferRecipientRegistrar.resolveTransferTarget(
                user,
                target,
                LocalDateTime.now()
        );
        return ValidatedTransferCommand.of(
                command.amount(),
                recipient,
                normalizeOptional(command.sourceAccountAlias())
        );
    }

    /**
     * 이름만 말한 경우. 주소록에 <b>정확히 같은 이름</b>이 있어야 한다.
     *
     * <p>없으면 오류로 끝내지 않고 되묻는다. 저장된 분이 없다는 말에서 대화가 끊기면
     * 사용자는 은행과 계좌번호를 말하면 보낼 수 있다는 것을 알 수 없다.
     */
    private TransferValidationResult resolveByName(
            final TransferCommandRequest command,
            final Optional<TransferRecipient> namedRecipient
    ) {
        if (namedRecipient.isEmpty()) {
            return TransferClarification.of(
                    List.of(TransferSlot.RECIPIENT),
                    UNKNOWN_RECIPIENT_QUESTION.formatted(command.recipient().trim())
            );
        }
        return ValidatedTransferCommand.of(
                command.amount(),
                requireVerified(namedRecipient.get()),
                normalizeOptional(command.sourceAccountAlias())
        );
    }

    /**
     * 저장된 수취인의 계좌를 이체 직전에 다시 확인한다.
     *
     * <p>검증 없이 접두어만 맞춰 저장되던 시절의 행이 남아 있다. 별칭 모양이나
     * {@code transferCount}로는 그것이 확인된 계좌인지 알 수 없으므로 은행에 다시 묻는다.
     * 확인되면 행에 남겨 다음부터는 되묻지 않는다.
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

    /** 주소록에서 정확히 같은 이름을 찾는다. 비슷한 이름으로 넓히지 않는다. */
    private Optional<TransferRecipient> findAddressBookEntry(
            final Long userId,
            final TransferCommandRequest command
    ) {
        if (isBlank(command.recipient()) || !isTrusted(command.recipientConfidence())) {
            return Optional.empty();
        }
        return transferRecipientRepository.findByUserIdAndAddressBookTrueAndNickname(
                userId,
                command.recipient().trim()
        );
    }

    private boolean pointsToSameAccount(
            final TransferRecipient recipient,
            final VerifiedTransferTarget target
    ) {
        return Objects.equals(recipient.getBankCode(), target.bankCode())
                && Objects.equals(recipient.getAccountNumHash(), target.accountNumHash());
    }

    private void validateOverallConfidence(final TransferCommandRequest command) {
        if (!isTrusted(command.sttConfidence()) || !isTrusted(command.intentConfidence())) {
            throw new BusinessException(ErrorCode.LOW_CONFIDENCE);
        }
    }

    private List<TransferSlot> findMissingSlots(final TransferCommandRequest command) {
        final List<TransferSlot> missingSlots = new ArrayList<>();
        if (!hasRecipient(command)) {
            missingSlots.add(TransferSlot.RECIPIENT);
        }
        if (command.amount() == null || !isTrusted(command.amountConfidence())) {
            missingSlots.add(TransferSlot.AMOUNT);
        }
        return missingSlots;
    }

    /**
     * 수취인을 정할 수 있는지.
     *
     * <p>계좌번호를 말해 줬으면 이름이 없어도 보낼 수 있다. 계좌번호는 STT 가 그대로 받아
     * 적은 숫자라 이름처럼 신뢰도를 따로 매기지 않고, 대신 확인 단계에서 자릿수를 하나씩
     * 읽어 사용자에게 되묻는다.
     */
    private boolean hasRecipient(final TransferCommandRequest command) {
        if (command.hasSpokenAccount()) {
            return true;
        }
        return !isBlank(command.recipient()) && isTrusted(command.recipientConfidence());
    }

    private String decryptOrNull(final String encrypted) {
        try {
            return sensitiveDataCrypto.decrypt(encrypted);
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    /**
     * 되물을 문장을 고른다.
     *
     * <p>수취인이 비었다고 늘 누구에게 보낼지만 되물으면 안 된다. 계좌번호는 말했는데
     * 은행만 빠진 사용자에게 그 문장은 답이 없는 질문이다 — 이미 누구에게 보낼지는
     * 말했기 때문이다. 무엇을 말해야 하는지 콕 집어 준다.
     */
    private TransferClarification createClarification(
            final TransferCommandRequest command,
            final List<TransferSlot> missingSlots
    ) {
        if (!missingSlots.contains(TransferSlot.RECIPIENT)) {
            return TransferClarification.of(missingSlots, AMOUNT_QUESTION);
        }
        if (hasAccountNumberOnly(command)) {
            return TransferClarification.of(missingSlots, BANK_QUESTION);
        }
        if (hasBankOnly(command)) {
            return TransferClarification.of(missingSlots, ACCOUNT_NUMBER_QUESTION);
        }
        return TransferClarification.of(missingSlots, RECIPIENT_QUESTION);
    }

    /** 계좌번호는 들었는데 은행을 못 들은 상태. */
    private boolean hasAccountNumberOnly(final TransferCommandRequest command) {
        return !isBlank(command.accountNumber()) && isBlank(command.bankCode());
    }

    /** 은행은 들었는데 계좌번호를 못 들은 상태. */
    private boolean hasBankOnly(final TransferCommandRequest command) {
        return isBlank(command.accountNumber()) && !isBlank(command.bankCode());
    }

    /**
     * 이체 금액이 정책 범위 안인지 확인한다.
     *
     * <p>직접 입력 송금도 같은 한도를 쓴다. 경로마다 한도를 따로 두면 한쪽만 고쳐 놓고
     * 다른 쪽으로 한도를 넘길 수 있다.
     */
    public void validateAmountRange(final long amount) {
        if (amount < transferProperties.minimumAmount()) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
        if (amount > transferProperties.perTransferLimit()) {
            throw new BusinessException(ErrorCode.AMOUNT_LIMIT_EXCEEDED);
        }
    }

    private boolean isTrusted(final BigDecimal confidence) {
        if (confidence == null) {
            return false;
        }
        if (confidence.compareTo(MAXIMUM_CONFIDENCE) > 0) {
            return false;
        }
        return confidence.compareTo(MINIMUM_CONFIDENCE) >= 0;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private String normalizeOptional(final String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
