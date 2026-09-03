package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.TransferRecipient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecipientRepository extends JpaRepository<TransferRecipient, Long> {

    /**
     * 주소록 목록.
     *
     * <p>{@code addressBook = false} 인 일회성 송금 대상은 제외한다. 사용자가 이름을 지은
     * 적 없는 행이라 목록에서 읽어 줄 이름이 없고, 고를 수도 없다.
     */
    List<TransferRecipient> findAllByUserIdAndAddressBookTrueOrderByNicknameAsc(Long userId);

    /** 이름으로 부른 상대. 주소록 항목만 대상이다. */
    Optional<TransferRecipient> findByUserIdAndAddressBookTrueAndNickname(
            Long userId,
            String nickname
    );

    boolean existsByUserIdAndAddressBookTrueAndNickname(Long userId, String nickname);

    /**
     * 거래 대상의 신원.
     *
     * <p>주소록 등록 여부와 무관하게 <b>같은 은행·같은 전체 계좌번호는 한 행</b>이다. 계좌번호는
     * 은행 안에서만 유일하므로 은행코드가 함께 있어야 한다 — 빼면 다른 은행의 같은 번호가
     * 중복으로 막히고, 반대로 은행만 보면 서로 다른 계좌가 한 행으로 합쳐진다.
     */
    Optional<TransferRecipient> findByUserIdAndBankCodeAndAccountNumHash(
            Long userId,
            String bankCode,
            String accountNumHash
    );

    /**
     * 검색 해시가 아직 없는 행. 일회성 백필
     * ({@code docs/migrations/20260903_add_recipient_account_num_hash.sql}) 전용이다.
     */
    List<TransferRecipient> findAllByAccountNumHashIsNull();
}
