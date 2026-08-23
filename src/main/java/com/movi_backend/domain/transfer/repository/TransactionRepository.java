package com.movi_backend.domain.transfer.repository;

import com.movi_backend.domain.transfer.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
