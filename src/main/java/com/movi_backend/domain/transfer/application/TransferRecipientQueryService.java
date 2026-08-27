package com.movi_backend.domain.transfer.application;

import com.movi_backend.domain.transfer.dto.response.RecipientListResponse;
import com.movi_backend.domain.transfer.dto.response.RecipientResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.SensitiveTextMasker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 수취인 조회.
 *
 * <p>음성을 쓸 수 없는 상황에서 키보드로 송금하려면 "누구에게"를 고를 수단이 필요하다.
 * 이 목록이 그 선택지 전부이고, 여기 없는 사람에게는 보낼 수 없다.
 */
@Service
@RequiredArgsConstructor
public class TransferRecipientQueryService {

    private final TransferRecipientRepository transferRecipientRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    @Transactional(readOnly = true)
    public RecipientListResponse findAll(final Long userId) {
        final List<TransferRecipient> recipients =
                transferRecipientRepository.findAllByUserIdOrderByNicknameAsc(userId);
        return RecipientListResponse.from(
                recipients.stream()
                        .map(recipient -> RecipientResponse.of(recipient, maskAccountNum(recipient)))
                        .toList()
        );
    }

    /**
     * 복호화 직후 곧바로 가린다.
     *
     * <p>평문 계좌번호가 DTO나 로그로 넘어가지 않도록, 복호화한 값은 이 메서드 밖으로 나가지
     * 않는다. 복호화가 실패해도 목록 전체를 실패시키지 않고 그 항목만 가린 채로 보여 준다 —
     * 계좌번호를 못 읽는 것이 수취인을 아예 고르지 못할 이유는 아니다.
     */
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
