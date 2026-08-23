package com.movi_backend.domain.fds.infrastructure;

import com.movi_backend.domain.fds.application.FdsProfileRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "movi.fds.profile-refresh",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FdsProfileRefreshScheduler {

    private final FdsProfileRefreshService refreshService;

    @Scheduled(
            cron = "${movi.fds.profile-refresh.cron:0 0 3 * * *}",
            zone = "Asia/Seoul"
    )
    public void refreshProfiles() {
        refreshService.refresh();
    }
}
