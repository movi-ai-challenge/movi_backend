package com.movi_backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * <p>엔드포인트별 설명은 각 컨트롤러의 {@code *ApiDocs} 인터페이스에 있다.
 * 어노테이션을 컨트롤러에 직접 달면 메서드 하나가 수십 줄이 되어 읽기 어려워지므로 분리했다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String DEV_USER_SCHEME = "devUser";

    private static final String LOCAL_SERVER_URL = "http://localhost:8080";

    private static final String DESCRIPTION = """
            시각장애인·시니어가 화면을 보지 않고 음성만으로 잔액을 확인하고 송금하는 Voice-First 뱅킹 API입니다.

            ## 이 API를 쓸 때 알아야 할 것

            **모든 응답은 `ApiResponse`로 감싸집니다.** 성공과 실패의 구조가 같습니다.

            ```json
            {
              "code": "SUCCESS",
              "message": "요청이 정상 처리되었습니다.",
              "voiceMessage": "국민은행 생활비 통장에 53만원 있어요.",
              "data": { }
            }
            ```

            **`voiceMessage`는 화면 없이 결과를 전달하는 문구입니다.** 프론트는 이 문장을 그대로 TTS로 읽으면 됩니다.
            금액은 백엔드가 한국어로 변환해 넣습니다(`53000원`이 아니라 `5만 3천원`) — TTS가 숫자를 어떻게 읽을지
            보장할 수 없기 때문입니다. 오류 응답에도 들어 있으니 예외 상황도 음성으로 안내할 수 있습니다.

            **오류는 HTTP 상태와 `code`를 함께 봅니다.** 전체 목록은 `docs/error-codes.md`에 있습니다.

            ## 인증

            운영 환경은 `Authorization: Bearer {accessToken}`이 필요합니다.
            로컬 개발(`movi.auth.dev-mode=true`)에서는 JWT 없이 `X-Dev-User-Id` 헤더로 사용자를 지정할 수 있습니다.
            **운영에서는 이 헤더가 동작하지 않습니다.**

            ## 음성 명령 흐름

            음성 기능은 단발 호출이 아니라 세션 위에서 이어집니다.

            1. `POST /api/voice/sessions` — 세션 시작
            2. `POST /api/voice/sessions/{id}/commands` — 발화 업로드
            3. 정보가 부족하면 `CLARIFYING` 상태로 재질문이 오고, 같은 세션에 다음 발화를 올리면 합쳐집니다
            4. 정보가 갖춰지면 `AWAITING_CONFIRMATION`으로 확인 문장이 옵니다
            5. 확인 발화를 올릴 때 `idempotencyKey`를 함께 보내면 송금이 실행됩니다

            **슬롯은 백엔드가 소유합니다.** 프론트와 AI는 앞선 발화의 금액·수취인을 보관하지 않습니다.
            """;

    @Bean
    public OpenAPI moviOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Movi Backend API")
                        .version("v1")
                        .description(DESCRIPTION))
                .servers(servers())
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, bearerScheme())
                        .addSecuritySchemes(DEV_USER_SCHEME, devUserScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * 요청을 보낼 서버 목록.
     *
     * <p>첫 항목을 절대 주소가 아닌 {@code /} 로 둔다. 문서를 연 주소가 그대로 요청 주소가 되므로
     * 배포 주소가 바뀌거나 HTTPS 가 붙어도 이 코드를 고칠 필요가 없다. 배포 주소를 문자열로
     * 박아 두면 그 시점마다 함께 고쳐야 하고, 잊으면 문서가 조용히 틀린 주소를 가리킨다.
     *
     * <p>로컬 서버를 따로 두는 이유는 <b>배포 문서에서 내 로컬 서버로 시험</b>하기 위해서다.
     */
    private List<Server> servers() {
        return List.of(
                new Server().url("/").description("현재 문서를 연 서버"),
                new Server().url(LOCAL_SERVER_URL).description("로컬 개발 서버")
        );
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 응답의 accessToken을 넣습니다. 만료되면 /api/v1/auth/token/refresh로 갱신합니다.");
    }

    private SecurityScheme devUserScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Dev-User-Id")
                .description("로컬 개발 전용. movi.auth.dev-mode=true 일 때만 동작하며 운영에서는 무시됩니다.");
    }
}
