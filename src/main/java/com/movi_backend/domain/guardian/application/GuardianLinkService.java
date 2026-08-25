package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 등록.
 *
 * <p>회원가입(또는 그 직후 온보딩)에서 보호자 전화번호를 입력하면 확인 절차 없이 바로
 * {@code ACTIVE} 연결이 생성된다. 보호자가 Movi 회원일 필요는 없다 — 알림은 전화번호로 보낸다.
 *
 * <p>중복 판별은 전부 {@code guardianPhoneHash} 기준이다. 암호문을 비교하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkService {

    private static final String VARIABLE_PROTECTEE_NAME = "protecteeName";

    private final GuardianLinkRepository guardianLinkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    /**
     * 7.1 보호자 등록.
     *
     * <p>연결 생성과 알림 이력 생성을 한 트랜잭션에서 처리한다. SMS 발송이 실패해도 예외를 올리지
     * 않으므로 이미 만들어진 연결은 남는다. 대신 응답 문구로 실패를 알린다.
     */
    @Transactional
    public GuardianLinkRegisterResponse register(
            final Long protecteeUserId,
            final GuardianLinkCreateRequest request
    ) {
        final User protectee = findActiveUser(protecteeUserId);
        final GuardianRelation relation = GuardianRelation.from(request.relation());
        final String normalizedPhone = PhoneNumberNormalizer.normalize(request.guardianPhone());
        final String guardianPhoneHash = sensitiveDataCrypto.hash(normalizedPhone);

        validateNotSelf(protectee, guardianPhoneHash);
        validateNotDuplicated(protecteeUserId, guardianPhoneHash);

        final GuardianLink guardianLink = guardianLinkRepository.save(GuardianLink.builder()
                .protecteeUser(protectee)
                .guardianName(request.guardianName().strip())
                .guardianPhone(sensitiveDataCrypto.encrypt(normalizedPhone))
                .guardianPhoneHash(guardianPhoneHash)
                .relation(relation)
                .permissionScope(GuardianPermissionScope.defaultScope().toJson())
                .build());

        final NotificationStatus notificationStatus =
                sendRegistrationNotice(protectee, guardianLink, normalizedPhone);

        auditLogService.record(
                protecteeUserId,
                ActorType.USER,
                AuditAction.GUARDIAN_LINK_REGISTERED,
                AuditAction.RESOURCE_GUARDIAN_LINK,
                guardianLink.getId()
        );
        return GuardianLinkRegisterResponse.from(guardianLink, notificationStatus);
    }

    private NotificationStatus sendRegistrationNotice(
            final User protectee,
            final GuardianLink guardianLink,
            final String normalizedPhone
    ) {
        final Map<String, String> variables = Map.of(VARIABLE_PROTECTEE_NAME, protectee.getName());
        return notificationService.send(NotificationRequest.guardianLinkRegistered(
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
     * <p>{@code REVOKED}는 중복으로 보지 않는다. 해제한 뒤 다시 등록할 수 있어야 한다.
     */
    private void validateNotDuplicated(final Long protecteeUserId, final String guardianPhoneHash) {
        final boolean alreadyLinked = guardianLinkRepository
                .existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
                        protecteeUserId, guardianPhoneHash, GuardianLinkStatus.ACTIVE);
        if (alreadyLinked) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED);
        }
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
