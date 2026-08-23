package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    List<GuardianLink> findAllByProtecteeUserIdAndStatus(
            Long protecteeUserId,
            GuardianLinkStatus status
    );
}
