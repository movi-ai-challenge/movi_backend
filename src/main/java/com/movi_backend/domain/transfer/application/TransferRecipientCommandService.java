package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.transfer.application.model.VerifiedTransferTarget;
import com.movi_backend.domain.transfer.dto.request.RecipientRegisterRequest;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.util.SensitiveTextMasker;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주소록에 상대방을 등록한다.
 *
 * <p>"엄마한테 5만원 보내줘"가 동작하려면 <b>이름과 계좌가 미리 묶여 있어야</b> 한다. 이
 * 서비스가 그 묶음을 만든다. 등록하지 않은 상대에게도 은행과 계좌번호를 말하면 보낼 수
 * 있지만, 이름으로 부르려면 여기를 거쳐야 한다.
 *
 * <p><b>등록 시점에 예금주조회로 계좌를 확인한다.</b> 송금하는 순간에 확인하면 이미 늦다 —
 * 사용자는 화면을 보지 않고 이름만 불렀는데, 그때 가서 그런 계좌가 없다고 하면 무엇이
 * 잘못됐는지 알 방법이 없다.
 *
 * <p>이미 그 계좌로 보낸 적이 있으면 그때 만들어진 행에 이름을 붙인다
 * ({@link TransferRecipientRegistrar}). 새 행을 만들면 이체 횟수가 쪼개져 FDS 가 여러 번
 * 보낸 상대를 처음 보는 상대로 본다.
 */
@Service
@RequiredArgsConstructor
public class TransferRecipientCommandService {

    private final UserRepository userRepository;
    private final TransferTargetVerifier transferTargetVerifier;
    private final TransferRecipientRegistrar transferRecipientRegistrar;

    @Transactional
    public RecipientResponse register(
            final Long userId,
            final RecipientRegisterRequest request
    ) {
        final String nickname = request.name().trim();
        if (nickname.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이름이 비어 있습니다.");
        }

        final VerifiedTransferTarget target = transferTargetVerifier.verifyForRegistration(
                userId,
                request.bankCode(),
                request.accountNumber()
        );
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final TransferRecipient saved = transferRecipientRegistrar.registerAddressBookEntry(
                user,
                nickname,
                target,
                LocalDateTime.now()
        );
        return RecipientResponse.of(
                saved,
                SensitiveTextMasker.maskAccountNumber(target.accountNumber())
        );
    }
}
