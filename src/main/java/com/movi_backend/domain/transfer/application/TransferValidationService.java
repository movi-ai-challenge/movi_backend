package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.application.model.TransferClarification;
import com.movi_backend.domain.transfer.application.model.TransferValidationResult;
import com.movi_backend.domain.transfer.application.model.ValidatedTransferCommand;
import com.movi_backend.domain.transfer.config.TransferProperties;
import com.movi_backend.domain.transfer.dto.request.TransferCommandRequest;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.type.TransferSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String AMBIGUOUS_RECIPIENT_QUESTION =
            "%s 님과 비슷한 이름이 여러 개 저장돼 있어요. 받는 분의 계좌번호를 말씀해 주세요.";

    /** 같은 별칭이 이만큼 쌓이면 사용자가 구분하지 못한다. 그 전에 멈춘다. */
    private static final int MAXIMUM_NICKNAME_SUFFIX = 20;

    private final TransferRecipientRepository transferRecipientRepository;
    private final TransferProperties transferProperties;
    private final UserRepository userRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final BankDirectory bankDirectory;

    /**
     * 계좌번호로 받은 수취인을 이 자리에서 저장하므로 읽기 전용이 아니다.
     *
     * <p>이체 실행은 {@code TransferRecipient} 엔티티를 요구하고 FDS 의 수취인 피처(재이체
     * 횟수·첫 거래 여부)도 거기 달려 있다. 저장하지 않고 임시 객체로 넘기면 그 피처가 매번
     * 비어 사용자가 늘 보내던 계좌인데도 신규로 평가된다.
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

        /*
         * 이름으로 부른 상대를 못 찾는 것은 오류가 아니라 되물을 일이다. 예외로 끝내면
         * "저장된 분이 없어요"에서 대화가 끊겨, 사용자는 계좌번호를 말하면 보낼 수 있다는
         * 것을 알 수 없다.
         */
        if (!command.hasSpokenAccount()) {
            final Optional<TransferRecipient> found = findRecipient(userId, command.recipient());
            if (found.isEmpty()) {
                return clarifyUnresolvedRecipient(userId, command.recipient());
            }
            return ValidatedTransferCommand.of(
                    command.amount(),
                    found.get(),
                    normalizeOptional(command.sourceAccountAlias())
            );
        }

        return ValidatedTransferCommand.of(
                command.amount(),
                findOrCreateByAccount(userId, command),
                normalizeOptional(command.sourceAccountAlias())
        );
    }

    /**
     * 이름을 못 찾았을 때 무엇을 되물을지 정한다.
     *
     * <p>저장된 게 아예 없는 것과, 비슷한 이름이 여럿이라 고르지 못한 것은 사용자에게 다른
     * 상황이다. 후자에 "저장된 분이 없어요"라고 하면 사실과 다르고, 사용자는 등록을 다시
     * 하려 든다. 둘 다 계좌번호로 풀리므로 그것을 요청하되 문장을 구분한다.
     */
    private TransferClarification clarifyUnresolvedRecipient(
            final Long userId,
            final String spokenName
    ) {
        final String name = spokenName.trim();
        final boolean hasSimilar = RecipientNicknameMatcher.hasAnyCloseMatch(
                name,
                transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(userId)
        );
        if (hasSimilar) {
            return TransferClarification.of(
                    List.of(TransferSlot.RECIPIENT),
                    AMBIGUOUS_RECIPIENT_QUESTION.formatted(name)
            );
        }
        return TransferClarification.of(
                List.of(TransferSlot.RECIPIENT),
                UNKNOWN_RECIPIENT_QUESTION.formatted(name)
        );
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

    /**
     * 계좌번호로 수취인을 찾고, 없으면 만들어 둔다.
     *
     * <p>사용자는 등록 절차를 겪지 않는다 — 계좌번호를 말하면 그걸로 끝이다. 다만 내부적으로는
     * 남겨야 이체 실행과 FDS 가 같은 수취인으로 인식하고, 다음에 같은 계좌로 보낼 때 재이체로
     * 평가된다.
     *
     * <p>암호문끼리는 비교할 수 없다. AES 가 무작위 IV 를 써서 같은 계좌번호도 암호문이 매번
     * 다르다. 한 사람의 수취인은 많아야 수십 명이므로 복호화해 비교한다.
     */
    private TransferRecipient findOrCreateByAccount(
            final Long userId,
            final TransferCommandRequest command
    ) {
        final String accountNumber = command.accountNumber();
        for (final TransferRecipient existing
                : transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(userId)) {
            if (!command.bankCode().equals(existing.getBankCode())) {
                continue;
            }
            if (accountNumber.equals(decryptOrNull(existing.getAccountNum()))) {
                return existing;
            }
        }

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return transferRecipientRepository.save(TransferRecipient.builder()
                .user(user)
                .nickname(generateNickname(userId, command.bankCode(), accountNumber))
                .bankCode(command.bankCode())
                .accountNum(sensitiveDataCrypto.encrypt(accountNumber))
                .accountNumHash(sensitiveDataCrypto.hash(accountNumber))
                .holderName("%s %s".formatted(
                        bankDirectory.displayNameOf(command.bankCode()),
                        accountNumber.substring(Math.max(0, accountNumber.length() - 4))))
                .build());
    }

    /**
     * 이름을 모르는 수취인의 별칭.
     *
     * <p>예금주명을 받을 방법이 없다. 목록에서 사용자가 알아볼 수 있어야 하므로 은행과 뒤
     * 네 자리로 만든다 — TTS 로 읽어도 구분된다.
     *
     * <p><b>겹치면 뒤에 번호를 붙인다.</b> 같은 은행에서 뒤 네 자리가 같은 계좌는 실제로
     * 생긴다. {@code (user_id, nickname)} 이 유니크라 그대로 저장하면 이체가 서버 오류로
     * 끝난다 — 사용자는 왜 실패했는지 알 수 없다.
     */
    private String generateNickname(
            final Long userId,
            final String bankCode,
            final String accountNumber
    ) {
        final String tail = accountNumber.substring(
                Math.max(0, accountNumber.length() - 4));
        final String base = "%s %s".formatted(bankDirectory.displayNameOf(bankCode), tail);
        if (!transferRecipientRepository.existsByUserIdAndNickname(userId, base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAXIMUM_NICKNAME_SUFFIX; suffix++) {
            final String candidate = "%s (%d)".formatted(base, suffix);
            if (!transferRecipientRepository.existsByUserIdAndNickname(userId, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND, "수취인 별칭을 만들지 못했습니다.");
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
     * <p>수취인이 비었다고 늘 "누구에게 보내시겠어요?"를 되물으면 안 된다. 계좌번호는
     * 말했는데 은행만 빠진 사용자에게 그 문장은 답이 없는 질문이다 — 이미 누구에게
     * 보낼지는 말했기 때문이다. 무엇을 말해야 하는지 콕 집어 준다.
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

    private Optional<TransferRecipient> findRecipient(
            final Long userId,
            final String recipientNickname
    ) {
        final String normalizedNickname = recipientNickname.trim();
        return transferRecipientRepository.findByUserIdAndNickname(userId, normalizedNickname)
                .or(() -> RecipientNicknameMatcher.findUniqueClosest(
                        normalizedNickname,
                        transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(userId)
                ));
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
