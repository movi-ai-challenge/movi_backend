package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.TransferRecipient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecipientRepository extends JpaRepository<TransferRecipient, Long> {

    List<TransferRecipient> findAllByUserIdOrderByNicknameAsc(Long userId);

    Optional<TransferRecipient> findByUserIdAndNickname(Long userId, String nickname);

    boolean existsByUserIdAndNickname(Long userId, String nickname);
}
