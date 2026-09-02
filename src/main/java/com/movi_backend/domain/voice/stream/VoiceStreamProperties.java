package com.movi_backend.domain.voice.stream;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실시간 음성 인식 중계 설정.
 *
 * @param enabled  스트리밍 중계를 켤지 여부. AI 쪽 WebSocket 이 준비되지 않은 환경에서는
 *                 꺼 둔다. 꺼 두면 핸들러가 등록되지 않아 연결 자체가 거부된다.
 * @param url      AI 서버의 WebSocket 주소. 컨테이너 이름으로 부르므로 백엔드도 같은
 *                 도커 네트워크(movi-net)에 있어야 한다.
 */
@ConfigurationProperties(prefix = "movi.voice.stream")
public record VoiceStreamProperties(Boolean enabled, String url) {

    private static final String DEFAULT_URL = "ws://movi-ai-voice:8001/internal/v1/voice/stream";

    public VoiceStreamProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (url == null || url.isBlank()) {
            url = DEFAULT_URL;
        }
    }

    public URI uri() {
        return URI.create(url);
    }
}
