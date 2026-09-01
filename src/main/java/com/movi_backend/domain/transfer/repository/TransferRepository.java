package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.type.TransferStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @EntityGraph(attributePaths = {"user", "recipient"})
    List<Transfer> findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            TransferStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    /**
     * 아직 끝나지 않은 이체가 이 계좌에 걸려 있는지 본다.
     *
     * <p>계좌 연결을 해제할 때 쓴다. 보내는 중인 돈이 있는 계좌를 끊으면 결과를 어디에도
     * 귀속시킬 수 없다.
     */
    boolean existsByFromAccountIdAndStatusIn(Long fromAccountId, Collection<TransferStatus> statuses);

    Optional<Transfer> findByIdAndUserId(Long transferId, Long userId);

    Optional<Transfer> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transfer
            from Transfer transfer
            where transfer.idempotencyKey = :idempotencyKey
              and transfer.user.id = :userId
            """)
    Optional<Transfer> findLockedByIdempotencyKeyAndUserId(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("userId") Long userId
    );

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
