package com.movi_backend.domain.account.infrastructure.openbanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오픈뱅킹 연동 설정.
 *
 * <p>인증정보는 gitignore 대상인 {@code application-local.yml} / 운영 시크릿에만 두고
 * 코드나 커밋되는 파일에 넣지 않는다.
 *
 * @param mode         {@code mock} 이면 Mock 어댑터, {@code real} 이면 실 API 어댑터를 쓴다
 * @param baseUrl      API 기본 URL. 테스트베드는 {@code https://testapi.openbanking.or.kr}
 * @param clientId     이용기관 Client ID
 * @param clientSecret 이용기관 Client Secret
 * @param redirectUri  인증 후 인가 코드를 받을 Callback URL.
 *                     <b>오픈뱅킹 포털에 등록한 값과 정확히 같아야 한다</b>
 * @param scope        요청할 권한 범위
 * @param clientUseCode 이용기관코드. 오픈뱅킹이 토큰 응답의 {@code client_use_code}로 알려준다.
 *                      거래고유번호(bank_tran_id) 앞자리에 쓰인다
 */
@ConfigurationProperties(prefix = "movi.openbanking")
public record OpenBankingProperties(
        String mode,
        String baseUrl,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String clientUseCode
) {
    public static final String MODE_MOCK = "mock";
    private static final String DEFAULT_BASE_URL = "https://testapi.openbanking.or.kr";
    private static final String DEFAULT_SCOPE = "login inquiry transfer";

    public OpenBankingProperties {
        if (mode == null || mode.isBlank()) {
            mode = MODE_MOCK;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (scope == null || scope.isBlank()) {
            scope = DEFAULT_SCOPE;
        }
    }

    public boolean isMock() {
        return MODE_MOCK.equalsIgnoreCase(this.mode);
    }
}
