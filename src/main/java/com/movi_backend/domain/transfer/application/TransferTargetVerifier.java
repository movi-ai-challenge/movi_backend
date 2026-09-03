package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.application.InternalAccountLocator;
import com.movi_backend.domain.account.application.port.AccountHolderInquiryPort;
import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌번호를 정규화하고 <b>예금주조회로 확인</b>한 뒤에야 송금 대상을 만든다.
 *
 * <p>송금 경로(음성·화면)와 주소록 등록이 모두 이 클래스를 지난다. 경로마다 따로 검증하면
 * 한쪽에만 규칙이 빠져, 등록은 막히는데 송금은 통과하는 상태가 된다 — 실제로 그랬다.
 *
 * <p>세 가지를 차례로 본다.
 *
 * <ol>
 *   <li><b>형식</b> — 하이픈·공백을 걷어낸 뒤 숫자 개수를 센다. {@code "------"} 처럼 숫자가
 *       없는 입력을 "없는 계좌"로 안내하면 사용자는 번호가 틀렸다고 생각해 같은 실수를 반복한다</li>
 *   <li><b>실재</b> — 은행코드와 전체 계좌번호가 정확히 맞는 계좌가 있는지 은행에 묻는다.
 *       접두어 일치·마스킹 번호 비교·음성 추정은 근거가 되지 않는다</li>
 *   <li><b>본인 계좌</b> — 자기 계좌로는 보내지 않는다</li>
 * </ol>
 *
 * <p>확인할 수단이 없으면 확인되지 않은 것으로 다룬다({@link AccountHolderInquiryPort}).
 */
@Service
@RequiredArgsConstructor
public class TransferTargetVerifier {

    /** 국내 계좌번호는 은행마다 자릿수가 다르다. 양 끝만 막아 오타를 걸러낸다. */
    private static final int MINIMUM_DIGITS = 6;
    private static final int MAXIMUM_DIGITS = 20;

    private final AccountHolderInquiryPort accountHolderInquiryPort;
    private final InternalAccountLocator internalAccountLocator;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /** 송금 대상으로 쓸 계좌를 확인한다. 본인 계좌면 이체할 수 없다고 안내한다. */
    @Transactional(readOnly = true)
    public VerifiedTransferTarget verifyForTransfer(
            final Long userId,
            final String bankCode,
            final String rawAccountNumber
    ) {
        return verify(userId, bankCode, rawAccountNumber, ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
    }

    /** 주소록에 올릴 계좌를 확인한다. 본인 계좌는 수취인으로 등록하지 않는다. */
    @Transactional(readOnly = true)
    public VerifiedTransferTarget verifyForRegistration(
            final Long userId,
            final String bankCode,
            final String rawAccountNumber
    ) {
        return verify(userId, bankCode, rawAccountNumber, ErrorCode.SELF_RECIPIENT_NOT_ALLOWED);
    }

    /**
     * 이미 저장된 수취인의 계좌를 다시 확인한다.
     *
     * <p>검증 없이 저장되던 시절의 행이 남아 있어, 이체 직전에 한 번 더 묻는다. 확인되면
     * 그 사실을 행에 남겨 다음부터는 되묻지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<VerifiedAccountHolder> reverify(
            final String bankCode,
            final String accountNumber
    ) {
        return accountHolderInquiryPort.inquire(bankCode, digitsOf(accountNumber));
    }

    /** 계좌 동일성 판단에 쓰는 해시. 저장·조회가 같은 정규화를 거치도록 한 곳에 둔다. */
    public String hashOf(final String accountNumber) {
        return sensitiveDataCrypto.hash(digitsOf(accountNumber));
    }

    private VerifiedTransferTarget verify(
            final Long userId,
            final String bankCode,
            final String rawAccountNumber,
            final ErrorCode selfAccountErrorCode
    ) {
        if (bankCode == null || bankCode.isBlank()) {
            throw new BusinessException(ErrorCode.BANK_CODE_MISSING);
        }
        final String accountNumber = requireWellFormed(rawAccountNumber);
        final VerifiedAccountHolder holder = accountHolderInquiryPort
                .inquire(bankCode.trim(), accountNumber)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_UNVERIFIED));

        requireNotOwnAccount(userId, holder, selfAccountErrorCode);

        return VerifiedTransferTarget.of(
                holder.bankCode(),
                holder.accountNumber(),
                sensitiveDataCrypto.hash(holder.accountNumber()),
                holder.holderName()
        );
    }

    private void requireNotOwnAccount(
            final Long userId,
            final VerifiedAccountHolder holder,
            final ErrorCode errorCode
    ) {
        final Optional<Account> ourAccount = internalAccountLocator.locate(
                holder.bankCode(),
                holder.accountNumber()
        );
        if (ourAccount.isEmpty()) {
            return;
        }
        if (Objects.equals(ourAccount.get().getUser().getId(), userId)) {
            throw new BusinessException(errorCode);
        }
    }

    private String requireWellFormed(final String rawAccountNumber) {
        if (rawAccountNumber == null || rawAccountNumber.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        if (!rawAccountNumber.matches("[0-9 \\-]+")) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        final String digits = digitsOf(rawAccountNumber);
        if (digits.length() < MINIMUM_DIGITS || digits.length() > MAXIMUM_DIGITS) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        return digits;
    }

    private String digitsOf(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }
}
