package com.movi_backend.domain.account.repository;

import com.movi_backend.domain.account.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserIdAndPrimaryTrue(Long userId);

    Optional<Account> findByUserIdAndAlias(Long userId, String alias);
}
