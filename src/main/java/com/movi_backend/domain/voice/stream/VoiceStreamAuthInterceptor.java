package com.movi_backend.domain.voice.stream;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket 핸드셰이크 인증.
 *
 * <p><b>토큰을 쿼리 파라미터로 받는다.</b> 브라우저의 WebSocket API 는 요청 헤더를
 * 지정할 수 없어 {@code Authorization} 을 붙일 방법이 없다. 대신 접근 토큰은 수명이
 * 30분으로 짧고, 이 값이 서버 접근 로그에 남을 수 있다는 점은 감수한다.
 *
 * <p>토큰이 없거나 유효하지 않으면 핸드셰이크 자체를 거부한다. 연결을 열어 두고
 * 나중에 닫으면 그 사이 오디오가 AI 로 흘러 인증 없이 STT 를 쓸 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class VoiceStreamAuthInterceptor implements HandshakeInterceptor {

    public static final String AUTH_USER_ATTRIBUTE = "movi.authUser";
    public static final String VOICE_SESSION_ATTRIBUTE = "movi.voiceSessionId";

    private static final String TOKEN_PARAMETER = "accessToken";
    private static final String VOICE_SESSION_PARAMETER = "voiceSessionId";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(
            final ServerHttpRequest request,
            final ServerHttpResponse response,
            final WebSocketHandler handler,
            final Map<String, Object> attributes
    ) {
        final var query = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        final String token = query.getFirst(TOKEN_PARAMETER);
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            final AuthUser authUser = jwtTokenProvider.parseAccessToken(token);
            attributes.put(AUTH_USER_ATTRIBUTE, authUser);
            attributes.put(
                    VOICE_SESSION_ATTRIBUTE,
                    parseVoiceSessionId(query.getFirst(VOICE_SESSION_PARAMETER))
            );
            return true;
        } catch (final BusinessException exception) {
            log.debug("음성 스트림 핸드셰이크를 거부했습니다: {}", exception.getErrorCode().getCode());
            return false;
        }
    }

    /**
     * 음성 세션 번호는 선택이다. 없으면 인식 결과만 흘려보내고 명령으로 처리하지
     * 않는다 -- 세션 없이 이체 판단을 시작할 수는 없다.
     */
    private Long parseVoiceSessionId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void afterHandshake(
            final ServerHttpRequest request,
            final ServerHttpResponse response,
            final WebSocketHandler handler,
            final Exception exception
    ) {
        // 남길 것이 없다.
    }
}
