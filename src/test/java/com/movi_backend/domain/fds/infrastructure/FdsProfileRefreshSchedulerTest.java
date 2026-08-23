package com.movi_backend.domain.fds.infrastructure;

import static org.mockito.BDDMockito.then;

import com.movi_backend.domain.fds.application.FdsProfileRefreshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsProfileRefreshSchedulerTest {

    @Mock
    private FdsProfileRefreshService refreshService;

    @InjectMocks
    private FdsProfileRefreshScheduler scheduler;

    @Test
    @DisplayName("스케줄 실행 시 FDS 프로필 갱신을 위임한다")
    void 스케줄_실행_시_FDS_프로필_갱신을_위임한다() {
        scheduler.refreshProfiles();

        then(refreshService).should().refresh();
    }
}
