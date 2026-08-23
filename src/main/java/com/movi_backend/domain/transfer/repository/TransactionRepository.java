package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.Transaction;
import com.movi_backend.domain.transfer.type.TransactionType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            select tx
            from Transaction tx
            where tx.account.id = :accountId
              and (:tranType is null or tx.tranType = :tranType)
              and (:startAt is null or tx.tranDatetime >= :startAt)
              and (:endAt is null or tx.tranDatetime < :endAt)
            """)
    Page<Transaction> findHistory(
            @Param("accountId") Long accountId,
            @Param("tranType") TransactionType tranType,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            Pageable pageable
    );
}
