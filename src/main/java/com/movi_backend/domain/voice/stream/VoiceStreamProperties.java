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
 * @param closeGraceSeconds
 *                 AI 가 끊은 뒤 브라우저 연결을 붙잡아 두는 시간(초).
 *                 <p>마지막 답을 보내자마자 닫으면, 왕복 지연이 큰 모바일 회선에서는
 *                 그 프레임이 도착하기 전에 연결이 끊길 수 있다. 실제로 아이폰에서
 *                 이체 확인 질문이 이 지점에서 사라졌다. 잠깐 열어 두면 전달이 끝난다.
 *                 <p>브라우저는 스스로 닫지 않으므로 언젠가는 서버가 닫아야 한다.
 *                 그래서 무한정이 아니라 이 시간만 기다린다.
 */
@ConfigurationProperties(prefix = "movi.voice.stream")
public record VoiceStreamProperties(Boolean enabled, String url, Integer closeGraceSeconds) {

    private static final String DEFAULT_URL = "ws://movi-ai-voice:8001/internal/v1/voice/stream";
    private static final int DEFAULT_CLOSE_GRACE_SECONDS = 10;

    public VoiceStreamProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (url == null || url.isBlank()) {
            url = DEFAULT_URL;
        }
        if (closeGraceSeconds == null || closeGraceSeconds < 0) {
            closeGraceSeconds = DEFAULT_CLOSE_GRACE_SECONDS;
        }
    }

    public URI uri() {
        return URI.create(url);
    }
}
