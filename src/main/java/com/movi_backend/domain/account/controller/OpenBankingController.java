package com.movi_backend.domain.account.controller;

import com.movi_backend.domain.account.application.OpenBankingConnectService;
import com.movi_backend.domain.account.controller.docs.OpenBankingApiDocs;
import com.movi_backend.domain.account.dto.response.ConnectStartResponse;
import com.movi_backend.domain.account.infrastructure.openbanking.OpenBankingProperties;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 오픈뱅킹 계좌 연결 (명세서 1.1, 1.2).
 */
@Slf4j
@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
public class OpenBankingController implements OpenBankingApiDocs {

    private static final String START_VOICE_MESSAGE = "은행 계좌를 연결할게요. 화면 안내를 따라 주세요.";

    private static final String RESULT_PARAM = "result";
    private static final String ERROR_PARAM = "error";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_ERROR = "error";

    /** 은행이 code 없이 돌려보냈을 때 쓰는 값. 사용자가 취소한 경우가 대부분이다. */
    private static final String MISSING_AUTHORIZATION_CODE = "missing_authorization_code";

    private final OpenBankingConnectService connectService;
    private final OpenBankingProperties openBankingProperties;

    /** 계좌 연결을 시작한다. 반환된 URL로 사용자를 보낸다. */
    @PostMapping("/connect")
    public ApiResponse<ConnectStartResponse> startConnect(@CurrentUser final AuthUser authUser) {
        final String url = connectService.startConnect(authUser.userId());
        return ApiResponse.success(ConnectStartResponse.of(url), START_VOICE_MESSAGE);
    }

    /**
     * 오픈뱅킹이 인가 코드를 돌려주는 콜백.
     *
     * <p>인증되지 않은 요청으로 들어오므로 {@code state}가 유일한 신원 증명이다.
     * 서비스에서 대조하고 즉시 폐기한다.
     *
     * <p><b>결과를 JSON 으로 돌려주지 않고 프런트 화면으로 302 로 보낸다.</b> 이 자리는
     * 사용자의 브라우저가 은행에서 돌아오는 지점이라, 본문을 반환하면 사용자가 JSON 을
     * 마주하고 흐름이 거기서 끊긴다. 화면을 보지 않는 사용자에게는 무엇이 잘못됐는지
     * 알 방법조차 없다.
     *
     * <p>성공·취소·실패를 모두 같은 프런트 주소로 돌려보내고 결과만 질의 문자열로 구분한다.
     * 실패했다고 사용자를 아무 데도 아닌 곳에 두지 않는다.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) final String code,
            @RequestParam(required = false) final String state,
            @RequestParam(name = ERROR_PARAM, required = false) final String error
    ) {
        if (error != null && !error.isBlank()) {
            log.info("오픈뱅킹 계좌 연결이 은행 쪽에서 중단됐습니다. error={}", error);
            return redirectTo(failureUri(error));
        }
        if (code == null || code.isBlank()) {
            log.info("오픈뱅킹 콜백에 인가 코드가 없습니다. 사용자가 취소했을 수 있습니다.");
            return redirectTo(failureUri(MISSING_AUTHORIZATION_CODE));
        }

        try {
            connectService.completeConnect(code, state);
            return redirectTo(successUri());
        } catch (final BusinessException exception) {
            // 계좌번호·토큰이 아니라 에러 코드만 싣는다. 이 주소는 브라우저 기록에 남는다.
            log.warn("오픈뱅킹 계좌 연결 실패. code={}", exception.getErrorCode().getCode());
            return redirectTo(failureUri(exception.getErrorCode().getCode()));
        }
    }

    private ResponseEntity<Void> redirectTo(final URI location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    private URI successUri() {
        return UriComponentsBuilder.fromUriString(requiredFrontendRedirectUri())
                .queryParam(RESULT_PARAM, RESULT_SUCCESS)
                .build()
                .encode()
                .toUri();
    }

    private URI failureUri(final String reason) {
        return UriComponentsBuilder.fromUriString(requiredFrontendRedirectUri())
                .queryParam(RESULT_PARAM, RESULT_ERROR)
                .queryParam(ERROR_PARAM, reason)
                .build()
                .encode()
                .toUri();
    }

    private String requiredFrontendRedirectUri() {
        final String frontendRedirectUri = openBankingProperties.frontendRedirectUri();
        if (frontendRedirectUri == null || frontendRedirectUri.isBlank()) {
            throw new IllegalStateException("오픈뱅킹 프론트엔드 Redirect URI 설정이 필요합니다.");
        }
        return frontendRedirectUri;
    }
}
