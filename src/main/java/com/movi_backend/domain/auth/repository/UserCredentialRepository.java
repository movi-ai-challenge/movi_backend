package com.movi_backend.domain.auth.repository;

import com.movi_backend.domain.auth.entity.UserCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    Optional<UserCredential> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
