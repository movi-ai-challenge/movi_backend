package com.movi_backend.domain.account.repository;

import com.movi_backend.domain.account.entity.BalanceSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, Long> {

    Optional<BalanceSnapshot> findTopByAccountIdOrderByFetchedAtDesc(Long accountId);
}
