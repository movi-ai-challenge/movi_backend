package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    /**
     * 같은 전화번호로 이미 연결된 보호자가 있는지 확인한다.
     *
     * <p>암호문이 아니라 HMAC 해시로 비교한다. AES가 무작위 IV를 쓰므로 암호문 비교는
     * 같은 번호를 다른 번호로 판정한다.
     */
    boolean existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
            Long protecteeUserId,
            String guardianPhoneHash,
            GuardianLinkStatus status
    );

    /** 알림을 받아야 할 보호자 목록. 고위험 이체 차단 시 사용한다. */
    List<GuardianLink> findAllByProtecteeUserIdAndStatus(
            Long protecteeUserId,
            GuardianLinkStatus status
    );
}
