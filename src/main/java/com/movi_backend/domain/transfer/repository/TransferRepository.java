package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.type.TransferStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdAndUserId(Long transferId, Long userId);

    Optional<Transfer> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    @Query("""
            select coalesce(sum(transfer.amount), 0)
            from Transfer transfer
            where transfer.user.id = :userId
              and transfer.status = :status
              and transfer.completedAt >= :startAt
              and transfer.completedAt < :endAt
            """)
    long sumAmountByUserAndStatusBetween(
            @Param("userId") Long userId,
            @Param("status") TransferStatus status,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
