package com.movi_backend.domain.guardian.application;

import com.movi_backend.domain.guardian.dto.response.NotificationResponse;
import com.movi_backend.domain.guardian.entity.Notification;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.SensitiveDataCrypto;
import com.movi_backend.global.util.PhoneNumberNormalizer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 알림 발송 기록 조회.
 *
 * <p>알림이 실제로 나갔는지 확인할 방법이 발송 제공자 콘솔과 DB뿐이라, 어느 쪽도 없는 환경에서는
 * "문자가 왔나요?"를 사람에게 물어보는 것 말고는 검증 수단이 없었다. 이 조회가 그 자리를 메운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_FIELD = "id";

    private final NotificationRepository notificationRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findMine(
            final Long userId,
            final int page,
            final int size
    ) {
        validatePageRequest(page, size);
        final Page<Notification> notifications = notificationRepository.findMine(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, SORT_FIELD))
        );
        final List<NotificationResponse> content = notifications.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(content, page, size, notifications.getTotalElements());
    }

    private NotificationResponse toResponse(final Notification notification) {
        return NotificationResponse.of(
                notification,
                maskGuardianPhone(notification.getGuardianLink().getGuardianPhone())
        );
    }

    /**
     * 복호화에 실패해도 목록 전체를 실패시키지 않는다.
     *
     * <p>이 조회는 발송이 어떻게 됐는지 보려고 부르는 것이다. 번호 한 건을 못 읽었다고 상태와
     * 재시도 횟수까지 못 보게 되면 정작 확인하려던 것을 못 본다.
     */
    private String maskGuardianPhone(final String encryptedPhone) {
        try {
            return PhoneNumberNormalizer.mask(sensitiveDataCrypto.decrypt(encryptedPhone));
        } catch (final RuntimeException exception) {
            log.debug("보호자 전화번호를 복호화하지 못해 마스킹 값을 비웁니다.", exception);
            return null;
        }
    }

    private void validatePageRequest(final int page, final int size) {
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "페이지 범위 오류");
        }
    }
}
