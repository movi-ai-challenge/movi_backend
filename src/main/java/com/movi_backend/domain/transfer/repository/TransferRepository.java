package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.Transfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdAndUserId(Long transferId, Long userId);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    Optional<Transfer> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);
}
