package com.movi_backend.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리 설정.
 *
 * <p>보호자 알림 발송은 이체 트랜잭션과 분리해야 한다. SMS Provider 장애가 이미 확정된
 * {@code BLOCKED} 상태를 되돌리면 안 되기 때문이다.
 *
 * <p>큐가 가득 차면 호출 스레드가 직접 실행한다({@code CallerRunsPolicy}). 알림을 조용히
 * 버리는 것보다 느리게라도 보내는 편이 안전하다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 200;
    private static final String THREAD_NAME_PREFIX = "movi-noti-";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
