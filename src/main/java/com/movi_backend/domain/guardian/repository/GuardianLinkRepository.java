package com.movi_backend.domain.guardian.repository;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    Optional<GuardianLink> findByInviteToken(String inviteToken);

    /** 이미 연결이 성립한 보호자인지 확인한다. 승인 시 2차 중복 검사에 쓴다. */
    boolean existsByProtecteeUserIdAndGuardianUserIdAndStatus(
            Long protecteeUserId,
            Long guardianUserId,
            GuardianLinkStatus status
    );

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

    /**
     * 아직 살아 있는 초대가 있는지 확인한다.
     *
     * <p>만료된 요청은 중복으로 보지 않는다. 초대 문자를 놓친 보호자에게 다시 보낼 수 있어야 한다.
     */
    @Query("""
            select count(link) > 0
            from GuardianLink link
            where link.protecteeUser.id = :protecteeUserId
              and link.guardianPhoneHash = :guardianPhoneHash
              and link.status = :status
              and link.inviteExpiresAt > :now
            """)
    boolean existsLivingInvitation(
            @Param("protecteeUserId") Long protecteeUserId,
            @Param("guardianPhoneHash") String guardianPhoneHash,
            @Param("status") GuardianLinkStatus status,
            @Param("now") LocalDateTime now
    );

    /** 알림을 받아야 할 보호자 목록. 고위험 이체 차단 시 사용한다. */
    List<GuardianLink> findAllByProtecteeUserIdAndStatus(
            Long protecteeUserId,
            GuardianLinkStatus status
    );

    /**
     * 초대를 승인한다. 조건부 UPDATE라 동시에 두 번 들어와도 한 번만 성공한다.
     *
     * <p>{@code REQUESTED}이면서 만료 전인 행만 바꾸므로, 영향받은 행이 0이면 이미 처리됐거나
     * 만료된 것이다. 호출부에서 현재 상태를 다시 읽어 정확한 예외로 바꾼다.
     *
     * @return 변경된 행 수 (0 또는 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GuardianLink link
            set link.guardianUser = :guardianUser,
                link.status = :approvedStatus,
                link.acceptedAt = :now
            where link.id = :linkId
              and link.status = :requiredStatus
              and link.inviteExpiresAt > :now
            """)
    int approveIfRequested(
            @Param("linkId") Long linkId,
            @Param("guardianUser") User guardianUser,
            @Param("approvedStatus") GuardianLinkStatus approvedStatus,
            @Param("requiredStatus") GuardianLinkStatus requiredStatus,
            @Param("now") LocalDateTime now
    );

    /** 초대를 거절한다. 승인과 마찬가지로 조건부 UPDATE로 한 번만 성공하게 한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GuardianLink link
            set link.status = :rejectedStatus
            where link.id = :linkId
              and link.status = :requiredStatus
            """)
    int rejectIfRequested(
            @Param("linkId") Long linkId,
            @Param("rejectedStatus") GuardianLinkStatus rejectedStatus,
            @Param("requiredStatus") GuardianLinkStatus requiredStatus
    );
}
