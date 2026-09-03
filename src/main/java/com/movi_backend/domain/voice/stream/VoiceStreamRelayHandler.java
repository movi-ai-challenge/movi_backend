package com.movi_backend.domain.voice.stream;

import com.movi_backend.domain.voice.application.VoiceCommandService;
import com.movi_backend.domain.voice.application.model.VoiceStreamContext;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.dto.response.VoiceCommandResponse;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.global.security.AuthUser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
    private final VoiceCommandService voiceCommandService;
    private final ObjectMapper objectMapper;

    /** 브라우저 세션 → AI 세션. 한쪽을 닫을 때 반대쪽을 찾아야 한다. */
    private final Map<String, WebSocketSession> upstreamByDownstream = new ConcurrentHashMap<>();

    /**
     * 브라우저 세션 → 분석 처리 진행 상태.
     *
     * <p>AI 는 분석 결과를 보낸 직후 인식 스트림이 끝나 곧바로 연결을 닫는다. 그런데 그
     * 결과를 실제 금융 흐름에 태우는 데는 DB·FDS·오픈뱅킹을 거쳐 수 초가 걸린다. AI 가
     * 닫았다고 브라우저까지 바로 닫으면, 답을 다 만들어 놓고 닫힌 소켓에 버리게 된다.
     * 사용자에게는 "잠시 문제가 생겼어요"만 남고 확인 질문은 영영 도착하지 않는다.
     */
    private final Map<String, RelayCloseCoordinator> coordinatorByDownstream =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(final WebSocketSession downstream) throws Exception {
        final AuthUser authUser = (AuthUser) downstream.getAttributes()
                .get(VoiceStreamAuthInterceptor.AUTH_USER_ATTRIBUTE);
        if (authUser == null) {
            // 인터셉터를 통과하지 못한 연결이다. 여기까지 오면 안 되지만, 오면 닫는다.
            downstream.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 세션 번호와 재질문 문맥을 AI 로 넘긴다. 되묻는 중이면 이어지는 발화가
        // "김민수"처럼 짧아, 무엇을 물어봤는지 모르면 전체 의도를 다시 분석하다
        // 이체라는 것을 잃어버린다.
        final Long voiceSessionId = (Long) downstream.getAttributes()
                .get(VoiceStreamAuthInterceptor.VOICE_SESSION_ATTRIBUTE);
        final String upstreamUrl = buildUpstreamUrl(authUser, voiceSessionId);

        final WebSocketSession upstream = new StandardWebSocketClient()
                .execute(new UpstreamHandler(downstream), upstreamUrl)
                .get();
        upstreamByDownstream.put(downstream.getId(), upstream);
        coordinatorByDownstream.put(downstream.getId(), new RelayCloseCoordinator());
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
        coordinatorByDownstream.remove(downstream.getId());
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


    private String buildUpstreamUrl(final AuthUser authUser, final Long voiceSessionId) {
        final StringBuilder url = new StringBuilder(properties.url())
                .append("?voiceSessionId=")
                .append(voiceSessionId == null ? 0L : voiceSessionId);
        if (voiceSessionId == null) {
            return url.toString();
        }

        final VoiceStreamContext context = readStreamContext(authUser, voiceSessionId);
        appendIfPresent(url, "expectedIntent", context.pendingIntentParameter());
        appendIfPresent(url, "expectedSlots", context.expectedSlotsParameter());
        return url.toString();
    }

    /**
     * 문맥을 못 읽어도 연결은 연다. 재질문 정보가 없으면 전체 분석으로 떨어질 뿐,
     * 사용자가 말을 시작하지 못하게 막을 이유는 아니다.
     */
    private VoiceStreamContext readStreamContext(
            final AuthUser authUser,
            final Long voiceSessionId
    ) {
        try {
            return voiceCommandService.findStreamContext(authUser.userId(), voiceSessionId);
        } catch (final RuntimeException exception) {
            log.debug("대화 문맥을 읽지 못했습니다: {}", exception.getClass().getSimpleName());
            return VoiceStreamContext.empty();
        }
    }

    private void appendIfPresent(
            final StringBuilder url,
            final String name,
            final String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        url.append('&')
                .append(name)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * AI 가 분석 결과를 보내오면 기존 명령 처리 흐름을 태운다.
     *
     * <p>인식 결과를 화면에 보여주는 것과, 그 말을 실제 금융 동작으로 잇는 것은 다른
     * 일이다. 여기서 {@code VoiceCommandService} 로 넘겨야 슬롯 검증·소유권·한도·FDS 가
     * 배치 경로와 똑같이 적용된다. 스트리밍만 다른 판단을 하면 한쪽에서만 막히는
     * 이체가 생긴다.
     *
     * <p>확인 발화와 실제 이체는 여기서 다루지 않는다. 확인에는 confirmationId 와
     * 멱등키가 필요하고, 그 교환은 기존 REST 흐름이 담당한다.
     */
    private void handleAnalysis(final WebSocketSession downstream, final String payload) {
        final JsonNode node = readJson(payload);
        if (node == null || !"analysis".equals(node.path("type").asString(null))) {
            return;
        }

        final AuthUser authUser = (AuthUser) downstream.getAttributes()
                .get(VoiceStreamAuthInterceptor.AUTH_USER_ATTRIBUTE);
        final Long voiceSessionId = (Long) downstream.getAttributes()
                .get(VoiceStreamAuthInterceptor.VOICE_SESSION_ATTRIBUTE);
        if (authUser == null || voiceSessionId == null) {
            // 세션 없이 연결한 경우다. 인식 결과만 보여주고 명령으로 처리하지 않는다.
            return;
        }

        try {
            final VoiceAnalysisResponse analysis =
                    objectMapper.treeToValue(node, VoiceAnalysisResponse.class);
            final VoiceCommandResponse response = voiceCommandService.processAnalyzed(
                    authUser.userId(),
                    voiceSessionId,
                    analysis,
                    null,
                    null
            );
            /*
             * 낭독 문구를 함께 보낸다. 화면을 보지 않는 사용자에게는 이 문장이
             * 유일한 안내다. 컨트롤러가 REST 응답에 싣는 것과 같은 값을 써야
             * 두 경로에서 다른 말이 들리지 않는다. 금액도 여기서 한국어로 바뀐다.
             */
            sendJson(downstream, Map.of(
                    "type", "command",
                    "data", response,
                    "voiceMessage", response.toVoiceMessage()
            ));
        } catch (final BusinessException exception) {
            sendJson(downstream, Map.of(
                    "type", "commandError",
                    "code", exception.getErrorCode().getCode(),
                    "voiceMessage", exception.getErrorCode().getVoiceMessage()
            ));
        } catch (final Exception exception) {
            log.warn("음성 명령 처리에 실패했습니다: {}", exception.getClass().getSimpleName());
            sendJson(downstream, Map.of(
                    "type", "commandError",
                    "code", ErrorCode.STT_FAILED.getCode(),
                    "voiceMessage", ErrorCode.STT_FAILED.getVoiceMessage()
            ));
        }
    }

    private JsonNode readJson(final String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (final Exception exception) {
            return null;
        }
    }

    private void sendJson(final WebSocketSession session, final Object body) {
        if (!session.isOpen()) {
            // 조용히 버리면 "답은 만들었는데 화면에는 오류" 를 추적할 단서가 없다.
            log.warn("브라우저 연결이 이미 닫혀 결과를 전달하지 못했습니다.");
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(body)));
        } catch (final Exception exception) {
            log.debug("결과 전송 실패: {}", exception.getClass().getSimpleName());
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
            if (!downstream.isOpen()) {
                return;
            }
            downstream.sendMessage(message);

            final RelayCloseCoordinator coordinator =
                    coordinatorByDownstream.get(downstream.getId());
            if (coordinator == null) {
                handleAnalysis(downstream, message.getPayload());
                return;
            }

            /*
             * 분석 처리 동안 AI 가 먼저 끊어도 브라우저를 닫지 않는다. 닫으면 결과를
             * 보낼 곳이 사라져, 성공한 이체 확인 질문이 사용자에게 도착하지 못한다.
             */
            coordinator.beginAnalysis();
            try {
                handleAnalysis(downstream, message.getPayload());
            } finally {
                if (coordinator.finishAnalysis()) {
                    closeQuietly(downstream);
                }
            }
        }

        @Override
        public void afterConnectionClosed(
                final WebSocketSession upstream,
                final CloseStatus status
        ) throws Exception {
            final RelayCloseCoordinator coordinator =
                    coordinatorByDownstream.get(downstream.getId());
            // 아직 분석 중이면 닫는 일은 그쪽에 맡긴다. 결과를 보낸 뒤에 닫혀야 한다.
            if (coordinator == null || coordinator.upstreamClosed()) {
                closeQuietly(downstream);
            }
        }
    }
}
