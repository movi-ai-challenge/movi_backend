package com.movi_backend.global.audit.application;

import com.movi_backend.global.audit.entity.AuditLog;
import com.movi_backend.global.audit.repository.AuditLogRepository;
import com.movi_backend.global.audit.type.ActorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 기록.
 *
 * <p><b>{@code detail}에 계좌번호·전화번호·초대 토큰을 넣지 않는다.</b> 감사 로그는 장기 보존
 * 대상이라 한 번 새어 나가면 회수할 수 없다. 식별자와 상태값만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(
            final Long userId,
            final ActorType actorType,
            final String action,
            final String resourceType,
            final Long resourceId
    ) {
        record(userId, actorType, action, resourceType, resourceId, null);
    }

    @Transactional
    public void record(
            final Long userId,
            final ActorType actorType,
            final String action,
            final String resourceType,
            final Long resourceId,
            final String detail
    ) {
        final AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .actorType(actorType)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .build();
        auditLogRepository.save(auditLog);
    }
}
