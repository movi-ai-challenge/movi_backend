package com.movi_backend.domain.voice.stream;

import com.movi_backend.global.security.AuthUser;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 브라우저와 AI 서버 사이의 음성 스트림 중계.
 *
 * <p><b>브라우저를 AI 서버에 직접 붙이지 않는다.</b> AI 는 내부망에만 두고 인증·검증을
 * 백엔드가 쥔다는 것이 이 서비스의 전제다. 브라우저가 AI 를 직접 부르면 누구나 인증
 * 없이 STT 를 호출할 수 있다.
 *
 * <p>중계는 두 방향이다. 브라우저 → AI 로는 오디오 조각을, AI → 브라우저 로는 인식
 * 결과를 흘린다. 한쪽이 끊기면 반대쪽도 닫는다 — 한쪽만 남으면 AI 세션이 계속 열려
 * Google 스트리밍 시간을 소모한다.
 */
@Slf4j
@RequiredArgsConstructor
public class VoiceStreamRelayHandler extends AbstractWebSocketHandler {

    private final VoiceStreamProperties properties;

    /** 브라우저 세션 → AI 세션. 한쪽을 닫을 때 반대쪽을 찾아야 한다. */
    private final Map<String, WebSocketSession> upstreamByDownstream = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(final WebSocketSession downstream) throws Exception {
        final AuthUser authUser = (AuthUser) downstream.getAttributes()
                .get(VoiceStreamAuthInterceptor.AUTH_USER_ATTRIBUTE);
        if (authUser == null) {
            // 인터셉터를 통과하지 못한 연결이다. 여기까지 오면 안 되지만, 오면 닫는다.
            downstream.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        final WebSocketSession upstream = new StandardWebSocketClient()
                .execute(new UpstreamHandler(downstream), properties.url())
                .get();
        upstreamByDownstream.put(downstream.getId(), upstream);
        log.info("음성 스트림 중계를 시작합니다: userId={}", authUser.userId());
    }

    @Override
    protected void handleBinaryMessage(
            final WebSocketSession downstream,
            final BinaryMessage message
    ) throws Exception {
        forward(downstream, message);
    }

    /** 브라우저가 보내는 "EOS"(더 보낼 오디오 없음)를 그대로 넘긴다. */
    @Override
    protected void handleTextMessage(
            final WebSocketSession downstream,
            final TextMessage message
    ) throws Exception {
        forward(downstream, message);
    }

    private void forward(
            final WebSocketSession downstream,
            final org.springframework.web.socket.WebSocketMessage<?> message
    ) throws IOException {
        final WebSocketSession upstream = upstreamByDownstream.get(downstream.getId());
        if (upstream == null || !upstream.isOpen()) {
            return;
        }
        upstream.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(
            final WebSocketSession downstream,
            final CloseStatus status
    ) throws Exception {
        final WebSocketSession upstream = upstreamByDownstream.remove(downstream.getId());
        closeQuietly(upstream);
    }

    @Override
    public void handleTransportError(
            final WebSocketSession downstream,
            final Throwable exception
    ) throws Exception {
        log.warn("음성 스트림 전송 오류: {}", exception.getClass().getSimpleName());
        closeQuietly(upstreamByDownstream.remove(downstream.getId()));
        closeQuietly(downstream);
    }

    private void closeQuietly(final WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close();
        } catch (final IOException exception) {
            log.debug("세션을 닫는 중 오류: {}", exception.getClass().getSimpleName());
        }
    }

    /** AI 가 보내는 인식 결과를 브라우저로 그대로 흘린다. */
    @RequiredArgsConstructor
    private final class UpstreamHandler extends AbstractWebSocketHandler {

        private final WebSocketSession downstream;

        @Override
        protected void handleTextMessage(
                final WebSocketSession upstream,
                final TextMessage message
        ) throws Exception {
            if (downstream.isOpen()) {
                downstream.sendMessage(message);
            }
        }

        @Override
        public void afterConnectionClosed(
                final WebSocketSession upstream,
                final CloseStatus status
        ) throws Exception {
            closeQuietly(downstream);
        }
    }
}
