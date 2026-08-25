package com.movi_backend.domain.fds.repository;

import com.movi_backend.domain.fds.entity.UserTransferProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTransferProfileRepository extends JpaRepository<UserTransferProfile, Long> {
}
