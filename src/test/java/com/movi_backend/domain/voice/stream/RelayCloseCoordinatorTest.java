package com.movi_backend.domain.voice.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RelayCloseCoordinator 는")
class RelayCloseCoordinatorTest {

    @Test
    @DisplayName("분석 중이 아닐 때 AI 가 끊으면 브라우저도 곧바로 닫는다")
    void closesImmediatelyWhenIdle() {
        final RelayCloseCoordinator coordinator = new RelayCloseCoordinator();

        assertThat(coordinator.upstreamClosed()).isTrue();
    }

    @Test
    @DisplayName("분석 중에 AI 가 끊으면 브라우저를 닫지 않는다")
    void keepsBrowserOpenWhileAnalysing() {
        final RelayCloseCoordinator coordinator = new RelayCloseCoordinator();
        coordinator.beginAnalysis();

        // 여기서 닫으면 결과를 보낼 곳이 사라진다.
        assertThat(coordinator.upstreamClosed()).isFalse();
    }

    @Test
    @DisplayName("분석이 끝났을 때 그 사이 AI 가 끊었으면 그때 닫는다")
    void closesAfterAnalysisWhenUpstreamAlreadyGone() {
        final RelayCloseCoordinator coordinator = new RelayCloseCoordinator();
        coordinator.beginAnalysis();
        coordinator.upstreamClosed();

        assertThat(coordinator.finishAnalysis()).isTrue();
    }

    @Test
    @DisplayName("AI 가 아직 살아 있으면 분석이 끝나도 닫지 않는다")
    void keepsBrowserOpenWhenUpstreamAlive() {
        final RelayCloseCoordinator coordinator = new RelayCloseCoordinator();
        coordinator.beginAnalysis();

        // 재질문처럼 이어지는 발화가 남아 있을 수 있다.
        assertThat(coordinator.finishAnalysis()).isFalse();
    }

    @Test
    @DisplayName("두 사건이 다른 스레드에서 겹쳐도 정확히 한 번만 닫는다")
    void closesExactlyOnceUnderRace() throws Exception {
        for (int attempt = 0; attempt < 500; attempt++) {
            final RelayCloseCoordinator coordinator = new RelayCloseCoordinator();
            coordinator.beginAnalysis();

            final AtomicBoolean closedByAnalysis = new AtomicBoolean(false);
            final AtomicBoolean closedByUpstream = new AtomicBoolean(false);
            final CountDownLatch start = new CountDownLatch(1);

            final Thread analysis = new Thread(() -> {
                await(start);
                closedByAnalysis.set(coordinator.finishAnalysis());
            });
            final Thread upstream = new Thread(() -> {
                await(start);
                closedByUpstream.set(coordinator.upstreamClosed());
            });
            analysis.start();
            upstream.start();
            start.countDown();
            analysis.join(2000);
            upstream.join(2000);

            // 아무도 닫지 않으면 연결이 새고, 둘 다 닫으면 중복 종료다.
            assertThat(closedByAnalysis.get() || closedByUpstream.get())
                    .as("어느 한쪽은 반드시 닫아야 한다 (attempt %d)", attempt)
                    .isTrue();
        }
    }

    private void await(final CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
