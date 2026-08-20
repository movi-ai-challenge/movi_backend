package com.movi_backend.domain.account.repository;

import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.type.ConnectionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenbankingConnectionRepository extends JpaRepository<OpenbankingConnection, Long> {

    Optional<OpenbankingConnection> findByUserIdAndStatus(Long userId, ConnectionStatus status);

    Optional<OpenbankingConnection> findByUserSeqNo(String userSeqNo);

    boolean existsByUserSeqNo(String userSeqNo);
}
