package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.guardian.dto.request.GuardianLinkCreateRequest;
import com.movi_backend.domain.guardian.dto.response.GuardianLinkRegisterResponse;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.type.GuardianLinkStatus;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 등록.
 *
 * <p>이용자가 보호자의 이름·전화번호를 입력하면 확인 절차 없이 바로 {@code ACTIVE} 연결이
 * 성립한다. 보호자가 Movi 회원일 필요는 없다 — 위험 거래 알림은 전화번호로 나간다.
 *
 * <p>여기서 만든 {@code ACTIVE} 링크가 있어야 {@code GuardianNotificationTransactionService}가
 * 알림 대상을 찾을 수 있다. 링크가 없으면 고위험 이체가 발생해도 보낼 곳이 없어 조용히 지나간다.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkService {

    /** {@code permission_scope} 기본값. 현재 읽는 곳은 없고 이력을 남기는 용도다. */
    private static final String DEFAULT_PERMISSION_SCOPE =
            "{\"view_balance\":true,\"receive_alert\":true}";

    private static final int INVITE_TOKEN_BYTE_LENGTH = 32;

    private final GuardianLinkRepository guardianLinkRepository;
    private final UserRepository userRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public GuardianLinkRegisterResponse register(
            final Long protecteeUserId,
            final GuardianLinkCreateRequest request
    ) {
        final User protectee = findActiveUser(protecteeUserId);
        final String normalizedPhone = PhoneNumberNormalizer.normalize(request.guardianPhone());

        validateNotSelf(protectee, normalizedPhone);
        validateNotDuplicated(protecteeUserId, normalizedPhone);

        final LocalDateTime now = LocalDateTime.now();
        final GuardianLink guardianLink = GuardianLink.builder()
                .protecteeUser(protectee)
                .guardianName(request.guardianName().strip())
                .guardianPhone(sensitiveDataCrypto.encrypt(normalizedPhone))
                .relation(normalizeRelation(request.relation()))
                .inviteToken(generateUnusedInviteToken())
                .inviteExpiresAt(now)
                .permissionScope(DEFAULT_PERMISSION_SCOPE)
                .build();
        guardianLink.activateWithoutInvite(now);

        return GuardianLinkRegisterResponse.from(guardianLinkRepository.save(guardianLink));
    }

    /**
     * 자기 자신을 보호자로 등록하려는 요청을 막는다.
     *
     * <p>암호문끼리 비교하지 않는다. AES가 무작위 IV를 쓰므로 같은 번호도 암호문이 매번 다르다.
     * {@code users.phone_hash}와 정규화된 번호의 HMAC을 비교한다. 카카오 가입 직후에는
     * {@code phoneHash}가 비어 있을 수 있고, 그때는 비교할 대상이 없어 통과시킨다.
     */
    private void validateNotSelf(final User protectee, final String normalizedPhone) {
        if (protectee.getPhoneHash() == null) {
            return;
        }
        if (protectee.getPhoneHash().equals(sensitiveDataCrypto.hash(normalizedPhone))) {
            throw new BusinessException(ErrorCode.SELF_LINK_NOT_ALLOWED);
        }
    }

    /**
     * 같은 번호를 두 번 등록하는 것을 막는다.
     *
     * <p>{@code guardian_links}에는 검색용 해시 컬럼이 없어 암호문으로는 걸러낼 수 없다.
     * 한 사람의 보호자 수는 많아야 몇 명이므로, 활성 링크만 복호화해 비교한다.
     * 해제({@code REVOKED})된 번호는 중복으로 보지 않는다 — 다시 등록할 수 있어야 한다.
     */
    private void validateNotDuplicated(final Long protecteeUserId, final String normalizedPhone) {
        final List<GuardianLink> activeLinks = guardianLinkRepository
                .findAllByProtecteeUserIdAndStatus(protecteeUserId, GuardianLinkStatus.ACTIVE);
        for (final GuardianLink link : activeLinks) {
            if (normalizedPhone.equals(sensitiveDataCrypto.decrypt(link.getGuardianPhone()))) {
                throw new BusinessException(ErrorCode.ALREADY_LINKED);
            }
        }
    }

    /**
     * 쓰이지 않는 초대 토큰을 만든다.
     *
     * <p>초대 흐름은 없지만 {@code invite_token}이 NOT NULL이고 UNIQUE 제약이 걸려 있어 값을
     * 채워야 한다. 값이 겹치면 등록이 실패하므로 난수를 쓴다. 만료 시각도 등록 시각으로 둬서,
     * 훗날 초대 기능이 되살아나도 이 토큰은 이미 만료된 상태로 취급되게 한다.
     */
    private String generateUnusedInviteToken() {
        final byte[] tokenBytes = new byte[INVITE_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String normalizeRelation(final String relation) {
        if (relation == null || relation.isBlank()) {
            return null;
        }
        return relation.strip();
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
