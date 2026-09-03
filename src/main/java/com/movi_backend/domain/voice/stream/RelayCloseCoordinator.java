package com.movi_backend.domain.voice.stream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 브라우저 연결을 <b>언제 닫아도 되는지</b> 정한다.
 *
 * <p>AI 는 분석 결과를 보낸 직후 인식 스트림이 끝나 곧바로 연결을 닫는다. 그런데 그 결과를
 * 실제 금융 흐름에 태우는 데는 DB·FDS·오픈뱅킹을 거쳐 수 초가 걸린다. AI 가 닫았다고
 * 브라우저까지 바로 닫으면, 답을 다 만들어 놓고 닫힌 소켓에 버리게 된다. 사용자 화면에는
 * "잠시 문제가 생겼어요"만 남고, 정작 이체 확인 질문은 영영 도착하지 않는다.
 *
 * <p>그래서 두 사건 중 <b>나중에 끝나는 쪽</b>이 닫는다. 분석이 진행 중이면 AI 가 끊어도
 * 닫지 않고, 분석이 끝날 때 그 사이 AI 가 끊었으면 그때 닫는다.
 *
 * <p>두 사건은 서로 다른 스레드에서 온다 — 메시지 수신과 연결 종료 콜백이다. 그래서 원자적
 * 플래그로 다룬다.
 */
final class RelayCloseCoordinator {

    private final AtomicBoolean analysisInFlight = new AtomicBoolean(false);
    private final AtomicBoolean upstreamClosed = new AtomicBoolean(false);

    /** 분석 처리를 시작한다. 이 동안에는 AI 가 끊어도 브라우저를 닫지 않는다. */
    void beginAnalysis() {
        analysisInFlight.set(true);
    }

    /**
     * 분석 처리가 끝났다.
     *
     * @return 브라우저 연결을 지금 닫아야 하면 {@code true}. 처리 중에 AI 가 이미 끊은 경우다
     */
    boolean finishAnalysis() {
        analysisInFlight.set(false);
        return upstreamClosed.get();
    }

    /**
     * AI 연결이 끊겼다.
     *
     * @return 브라우저 연결을 지금 닫아야 하면 {@code true}. 분석 중이면 {@code false} 이고,
     *         닫는 일은 {@link #finishAnalysis()} 쪽이 맡는다
     */
    boolean upstreamClosed() {
        upstreamClosed.set(true);
        return !analysisInFlight.get();
    }
}
