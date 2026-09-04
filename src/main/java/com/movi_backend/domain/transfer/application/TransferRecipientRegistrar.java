package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
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
 * 확인된 계좌에 대응하는 {@link TransferRecipient} 행을 만들거나 찾는다.
 *
 * <p><b>같은 사용자·같은 은행·같은 전체 계좌번호는 언제나 한 행이다.</b> 일회성으로 보낸
 * 계좌를 나중에 "엄마"로 등록해도 새 행을 만들지 않고 그 행을 주소록 항목으로 올린다.
 * 새로 만들면 {@code transferCount}가 쪼개져, 여러 번 보낸 상대가 FDS 에 처음 보내는
 * 상대로 간다.
 *
 * <p><b>별칭을 지어내지 않는다.</b> 일회성 대상은 {@code nickname} 이 {@code null} 이고
 * 주소록에 나오지 않는다. 예전에는 {@code "국민은행 6789"}, 겹치면 {@code "(2)"}를 붙여
 * 저장했는데 사용자가 짓지 않은 이름이라 부를 수도 지울 수도 없었다.
 */
@Service
@RequiredArgsConstructor
public class TransferRecipientRegistrar {

    private final TransferRecipientRepository transferRecipientRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 송금 대상 행을 찾거나 만든다. 주소록에는 올리지 않는다.
     *
     * <p>행을 만들었다는 것이 "보낸 적 있다"는 뜻은 아니다. {@code transferCount}는 이체가
     * 성공한 뒤에만 오르므로, 확인을 취소해도 다음번 FDS 판정은 그대로 첫 거래다.
     */
    @Transactional
    public TransferRecipient resolveTransferTarget(
            final User user,
            final VerifiedTransferTarget target,
            final LocalDateTime now
    ) {
        final Optional<TransferRecipient> existing = findByAccount(user.getId(), target);
        if (existing.isPresent()) {
            final TransferRecipient recipient = existing.get();
            recipient.verify(target.holderName(), now);
            return recipient;
        }
        return save(
                newRecipient(user, null, target, false, now),
                ErrorCode.DUPLICATE_TRANSFER
        );
    }

    /**
     * 주소록에 이름을 지어 올린다.
     *
     * <p>이미 그 계좌로 보낸 적이 있어 일회성 행이 있으면 <b>그 행을 올린다</b>. 이미 주소록
     * 항목이면 다른 이름으로 또 올리지 않는다 — "엄마"와 "어머니"가 같은 계좌를 가리키면
     * 음성에서 어느 쪽인지 정할 수 없다.
     */
    @Transactional
    public TransferRecipient registerAddressBookEntry(
            final User user,
            final String nickname,
            final VerifiedTransferTarget target,
            final LocalDateTime now
    ) {
        if (transferRecipientRepository
                .existsByUserIdAndAddressBookTrueAndNickname(user.getId(), nickname)) {
            throw new BusinessException(ErrorCode.RECIPIENT_NICKNAME_DUPLICATED);
        }

        final Optional<TransferRecipient> existing = findByAccount(user.getId(), target);
        if (existing.isPresent()) {
            final TransferRecipient recipient = existing.get();
            if (recipient.isAddressBook()) {
                throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED);
            }
            recipient.verify(target.holderName(), now);
            recipient.promoteToAddressBook(nickname);
            return recipient;
        }
        return save(
                newRecipient(user, nickname, target, true, now),
                ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED
        );
    }

    private Optional<TransferRecipient> findByAccount(
            final Long userId,
            final VerifiedTransferTarget target
    ) {
        return transferRecipientRepository.findByUserIdAndBankCodeAndAccountNumHash(
                userId,
                target.bankCode(),
                target.accountNumHash()
        );
    }

    private TransferRecipient newRecipient(
            final User user,
            final String nickname,
            final VerifiedTransferTarget target,
            final boolean addressBook,
            final LocalDateTime now
    ) {
        return TransferRecipient.builder()
                .user(user)
                .nickname(nickname)
                .bankCode(target.bankCode())
                .accountNum(sensitiveDataCrypto.encrypt(target.accountNumber()))
                .accountNumHash(target.accountNumHash())
                .holderName(target.holderName())
                .addressBook(addressBook)
                .verifiedAt(now)
                .build();
    }

    /**
     * 같은 계좌를 동시에 저장하려던 요청 하나는 UNIQUE 제약에 걸린다.
     *
     * <p>여기서 잡아 안내 가능한 예외로 바꾼다. <b>잡고 나서 같은 트랜잭션을 계속 쓰지
     * 않는다</b> — 제약 위반으로 롤백 표시가 붙은 트랜잭션에서 조회하거나 또 저장하면
     * 커밋 시점에 다시 터져 사용자는 500 을 받는다. 트랜잭션을 접고, 사용자는 안내를 듣고
     * 다시 시도한다.
     */
    private TransferRecipient save(
            final TransferRecipient recipient,
            final ErrorCode duplicateErrorCode
    ) {
        try {
            return transferRecipientRepository.saveAndFlush(recipient);
        } catch (final DataIntegrityViolationException exception) {
            throw new BusinessException(duplicateErrorCode);
        }
    }
}
