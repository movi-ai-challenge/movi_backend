package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianInvitationResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkApprovalResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRejectionResponse;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRequestResponse;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.domain.guardian.type.GuardianPermissionScope;
import com.movi_backend.domain.guardian.type.GuardianRelation;
import com.movi_backend.domain.guardian.type.NotificationStatus;
import com.movi_backend.domain.notification.application.NotificationService;
import com.movi_backend.domain.notification.dto.NotificationRequest;
import com.movi_backend.global.audit.application.AuditAction;
import com.movi_backend.global.audit.application.AuditLogService;
import com.movi_backend.global.audit.type.ActorType;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 연결 요청·확인·승인·거절.
 *
 * <p>연결은 요청만으로 성립하지 않는다. 보호자가 초대 링크로 내용을 확인하고 승인해야
 * {@code ACTIVE}가 된다. 그전까지 보호자는 피보호자의 어떤 정보도 볼 수 없다.
 *
 * <p>중복 판별은 전부 {@code guardianPhoneHash} 기준이다. 암호문을 비교하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkService {

    private static final String VARIABLE_PROTECTEE_NAME = "protecteeName";
    private static final String VARIABLE_GUARDIAN_NAME = "guardianName";
    private static final String VARIABLE_INVITE_URL = "inviteUrl";

    private final GuardianLinkRepository guardianLinkRepository;
    private final UserRepository userRepository;
    private final GuardianInvitationService guardianInvitationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 7.1 보호자 등록 요청.
     *
     * <p>연결 생성과 알림 이력 생성을 한 트랜잭션에서 처리한다. SMS 발송이 실패해도 예외를 올리지
     * 않으므로 이미 만들어진 연결 요청은 남는다. 대신 응답 문구로 실패를 알린다.
     */
    @Transactional
    public GuardianLinkRequestResponse request(
            final Long protecteeUserId,
            final GuardianLinkCreateRequest request
    ) {
        final User protectee = findActiveUser(protecteeUserId);
        final GuardianRelation relation = GuardianRelation.from(request.relation());
        final String normalizedPhone = PhoneNumberNormalizer.normalize(request.guardianPhone());
        final String guardianPhoneHash = sensitiveDataCrypto.hash(normalizedPhone);

        validateNotSelf(protectee, guardianPhoneHash);
        final LocalDateTime now = LocalDateTime.now();
        validateNotDuplicated(protecteeUserId, guardianPhoneHash, now);

        final String inviteToken = guardianInvitationService.generateInviteToken();
        final GuardianLink guardianLink = guardianLinkRepository.save(GuardianLink.builder()
                .protecteeUser(protectee)
                .guardianName(request.guardianName().strip())
                .guardianPhone(sensitiveDataCrypto.encrypt(normalizedPhone))
                .guardianPhoneHash(guardianPhoneHash)
                .relation(relation)
                .inviteToken(inviteToken)
                .inviteExpiresAt(guardianInvitationService.calculateExpiresAt(now))
                .permissionScope(GuardianPermissionScope.defaultScope().toJson())
                .build());

        final NotificationStatus notificationStatus = sendInvitation(
                protectee, guardianLink, normalizedPhone, inviteToken);

        auditLogService.record(
                protecteeUserId,
                ActorType.USER,
                AuditAction.GUARDIAN_LINK_REQUESTED,
                AuditAction.RESOURCE_GUARDIAN_LINK,
                guardianLink.getId()
        );
        return GuardianLinkRequestResponse.from(guardianLink, notificationStatus);
    }

    /** 7.2 초대 링크로 요청 내용을 확인한다. 로그인 없이 호출된다. */
    @Transactional(readOnly = true)
    public GuardianInvitationResponse findInvitation(final String inviteToken) {
        final GuardianLink guardianLink = findLivingInvitation(inviteToken, LocalDateTime.now());
        return GuardianInvitationResponse.from(guardianLink);
    }

    /**
     * 7.3 연결 승인.
     *
     * <p>조건부 UPDATE로 상태를 바꾼다. 같은 링크에 승인 요청이 동시에 들어와도 {@code REQUESTED}
     * 행을 먼저 잡은 한 건만 성공한다.
     */
    @Transactional
    public GuardianLinkApprovalResponse approve(final Long guardianUserId, final String inviteToken) {
        final User guardian = findActiveUser(guardianUserId);
        final LocalDateTime now = LocalDateTime.now();
        final GuardianLink guardianLink = findLivingInvitation(inviteToken, now);
        final Long protecteeUserId = guardianLink.getProtecteeUser().getId();

        validateNotSelfLink(protecteeUserId, guardianUserId);
        validateNoActiveLinkWith(protecteeUserId, guardianUserId);

        final int updatedRows = guardianLinkRepository.approveIfRequested(
                guardianLink.getId(),
                guardian,
                GuardianLinkStatus.ACTIVE,
                GuardianLinkStatus.REQUESTED,
                now
        );
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
        }

        final GuardianLink approved = findById(guardianLink.getId());
        auditLogService.record(
                guardianUserId,
                ActorType.GUARDIAN,
                AuditAction.GUARDIAN_LINK_APPROVED,
                AuditAction.RESOURCE_GUARDIAN_LINK,
                approved.getId()
        );
        return GuardianLinkApprovalResponse.from(approved);
    }

    /** 7.3 연결 거절. */
    @Transactional
    public GuardianLinkRejectionResponse reject(final Long guardianUserId, final String inviteToken) {
        findActiveUser(guardianUserId);
        final GuardianLink guardianLink = findLivingInvitation(inviteToken, LocalDateTime.now());

        final int updatedRows = guardianLinkRepository.rejectIfRequested(
                guardianLink.getId(),
                GuardianLinkStatus.REJECTED,
                GuardianLinkStatus.REQUESTED
        );
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
        }

        final GuardianLink rejected = findById(guardianLink.getId());
        auditLogService.record(
                guardianUserId,
                ActorType.GUARDIAN,
                AuditAction.GUARDIAN_LINK_REJECTED,
                AuditAction.RESOURCE_GUARDIAN_LINK,
                rejected.getId()
        );
        return GuardianLinkRejectionResponse.from(rejected);
    }

    private NotificationStatus sendInvitation(
            final User protectee,
            final GuardianLink guardianLink,
            final String normalizedPhone,
            final String inviteToken
    ) {
        final Map<String, String> variables = Map.of(
                VARIABLE_PROTECTEE_NAME, protectee.getName(),
                VARIABLE_GUARDIAN_NAME, guardianLink.getGuardianName(),
                VARIABLE_INVITE_URL, guardianInvitationService.buildInviteUrl(inviteToken)
        );
        return notificationService.send(NotificationRequest.guardianInvite(
                null,
                guardianLink.getId(),
                normalizedPhone,
                variables
        ));
    }

    /**
     * 자기 자신을 보호자로 등록하려는 요청을 막는다.
     *
     * <p>암호문끼리 비교하지 않는다. {@code users.phone_hash}와 정규화된 보호자 번호의 HMAC을
     * 비교한다. 회원가입 경로에 따라 {@code phoneHash}가 비어 있을 수 있어 그때는 비교하지 않는다.
     */
    private void validateNotSelf(final User protectee, final String guardianPhoneHash) {
        if (protectee.getPhoneHash() == null) {
            return;
        }
        if (protectee.getPhoneHash().equals(guardianPhoneHash)) {
            throw new BusinessException(ErrorCode.SELF_LINK_NOT_ALLOWED);
        }
    }

    /**
     * 7.7 중복 연결 방지.
     *
     * <p>{@code REJECTED}·{@code REVOKED}와 만료된 초대는 중복으로 보지 않는다. 거절당했거나
     * 문자를 놓친 뒤 다시 요청할 수 있어야 한다.
     */
    private void validateNotDuplicated(
            final Long protecteeUserId,
            final String guardianPhoneHash,
            final LocalDateTime now
    ) {
        final boolean alreadyLinked = guardianLinkRepository
                .existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
                        protecteeUserId, guardianPhoneHash, GuardianLinkStatus.ACTIVE);
        if (alreadyLinked) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED);
        }

        final boolean invitationAlive = guardianLinkRepository.existsLivingInvitation(
                protecteeUserId, guardianPhoneHash, GuardianLinkStatus.REQUESTED, now);
        if (invitationAlive) {
            throw new BusinessException(ErrorCode.DUPLICATE_GUARDIAN_REQUEST);
        }
    }

    private void validateNotSelfLink(final Long protecteeUserId, final Long guardianUserId) {
        if (protecteeUserId.equals(guardianUserId)) {
            throw new BusinessException(ErrorCode.SELF_LINK_NOT_ALLOWED);
        }
    }

    /**
     * 승인 시점의 2차 중복 검사.
     *
     * <p>요청 시점에는 보호자가 미가입이라 전화번호로만 걸렀다. 승인 시점에는 회원 ID가 확정되므로
     * 다른 번호로 초대받은 같은 사람이 이미 연결돼 있는지 여기서 다시 본다.
     */
    private void validateNoActiveLinkWith(final Long protecteeUserId, final Long guardianUserId) {
        final boolean alreadyLinked = guardianLinkRepository
                .existsByProtecteeUserIdAndGuardianUserIdAndStatus(
                        protecteeUserId, guardianUserId, GuardianLinkStatus.ACTIVE);
        if (alreadyLinked) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED);
        }
    }

    /** 존재·상태·만료를 한 번에 검증한다. 존재하지 않는 토큰과 잘못된 토큰을 구분해 알리지 않는다. */
    private GuardianLink findLivingInvitation(final String inviteToken, final LocalDateTime now) {
        final GuardianLink guardianLink = guardianLinkRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_TOKEN));
        if (!guardianLink.isRequested()) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
        }
        if (guardianLink.isInviteExpired(now)) {
            throw new BusinessException(ErrorCode.INVITE_EXPIRED);
        }
        return guardianLink;
    }

    private GuardianLink findById(final Long linkId) {
        return guardianLinkRepository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));
    }

    private User findActiveUser(final Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
