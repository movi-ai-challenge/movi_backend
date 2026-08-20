package com.movi_backend.domain.account.repository;

import com.movi_backend.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    Optional<Account> findByUserIdAndPrimaryIsTrue(Long userId);

    List<Account> findAllByUserIdAndActiveIsTrue(Long userId);
}
