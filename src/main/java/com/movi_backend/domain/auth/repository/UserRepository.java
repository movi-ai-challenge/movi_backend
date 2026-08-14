package com.movi_backend.domain.auth.repository;

import com.movi_backend.domain.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneHash(String phoneHash);
}
