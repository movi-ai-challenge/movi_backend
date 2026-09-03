package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.TransferRecipient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecipientRepository extends JpaRepository<TransferRecipient, Long> {

    List<TransferRecipient> findAllByUserIdOrderByNicknameAsc(Long userId);

    Optional<TransferRecipient> findByUserIdAndNickname(Long userId, String nickname);

    boolean existsByUserIdAndNickname(Long userId, String nickname);

    boolean existsByUserIdAndAccountNumHash(Long userId, String accountNumHash);

    /**
     * 검색 해시가 아직 없는 행. 일회성 백필
     * ({@code docs/migrations/20260903_add_recipient_account_num_hash.sql}) 전용이다.
     */
    List<TransferRecipient> findAllByAccountNumHashIsNull();
}
