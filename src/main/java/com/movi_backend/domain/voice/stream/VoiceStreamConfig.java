package com.movi_backend.domain.voice.stream;

import com.movi_backend.domain.voice.application.VoiceCommandService;
import com.movi_backend.global.config.CorsProperties;
import com.movi_backend.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * 실시간 음성 인식 WebSocket 등록.
 *
 * <p>{@code movi.voice.stream.enabled=true} 일 때만 켠다. AI 쪽 WebSocket 이 없는
 * 환경에서 연결을 받아 두면 브라우저는 연결된 줄 알고 오디오를 보내다 아무 결과도
 * 받지 못한다. 아예 열지 않는 편이 낫다.
 *
 * <p>허용 오리진은 {@link CorsProperties} 를 그대로 쓴다. WebSocket 은 CORS 의
 * 적용을 받지 않아 여기서 따로 막지 않으면 어느 사이트에서든 붙을 수 있다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movi.voice.stream", name = "enabled", havingValue = "true")
public class VoiceStreamConfig implements WebSocketConfigurer {

    private static final String STREAM_PATH = "/ws/v1/voice/stream";

    private final VoiceStreamProperties properties;
    private final CorsProperties corsProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final VoiceCommandService voiceCommandService;
    private final ObjectMapper objectMapper;

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry.addHandler(
                        new VoiceStreamRelayHandler(
                                properties,
                                voiceCommandService,
                                objectMapper
                        ),
                        STREAM_PATH
                )
                .addInterceptors(new VoiceStreamAuthInterceptor(jwtTokenProvider))
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new));
    }
}
