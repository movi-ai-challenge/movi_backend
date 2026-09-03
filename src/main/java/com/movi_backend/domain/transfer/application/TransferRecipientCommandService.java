package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.account.application.RegisteredAccountFinder;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.SensitiveTextMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상대방 등록.
 *
 * <p>"엄마한테 5만원 보내줘"가 동작하려면 <b>이름과 계좌가 미리 묶여 있어야</b> 한다. 이
 * 서비스가 그 묶음을 만든다. 여기 등록되지 않은 사람은 이름으로 부를 수 없고, 계좌번호를
 * 전부 말해야 한다.
 *
 * <p>등록 시점에 계좌가 실재하는지 확인한다. 송금하는 순간에 확인하면 이미 늦다 — 사용자는
 * 화면을 보지 않고 이름만 불렀는데, 그때 가서 "그런 계좌가 없다"고 하면 무엇이 잘못됐는지
 * 알 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class TransferRecipientCommandService {

    private final TransferRecipientRepository transferRecipientRepository;
    private final RegisteredAccountFinder registeredAccountFinder;
    private final UserRepository userRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    @Transactional
    public RecipientResponse register(
            final Long userId,
            final RecipientRegisterRequest request
    ) {
        final String nickname = request.name().trim();
        final String accountNumber = digitsOf(request.accountNumber());

        // 별칭이 겹치면 음성에서 누구를 가리키는지 정할 수 없다. 저장 전에 막는다.
        if (transferRecipientRepository.existsByUserIdAndNickname(userId, nickname)) {
            throw new BusinessException(ErrorCode.RECIPIENT_NICKNAME_DUPLICATED);
        }

        final Account account = registeredAccountFinder.findByAccountNumber(accountNumber);
        if (account.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_RECIPIENT_NOT_ALLOWED);
        }

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final TransferRecipient saved = transferRecipientRepository.save(
                TransferRecipient.builder()
                        .user(user)
                        .nickname(nickname)
                        .bankCode(account.getBankCode())
                        .accountNum(sensitiveDataCrypto.encrypt(accountNumber))
                        .holderName(account.getUser().getName())
                        .build()
        );

        return RecipientResponse.of(
                saved,
                SensitiveTextMasker.maskAccountNumber(accountNumber)
        );
    }

    private String digitsOf(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
