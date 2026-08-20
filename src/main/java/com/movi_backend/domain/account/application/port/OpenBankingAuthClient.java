package com.movi_backend.domain.account.application.port;

import com.movi_backend.domain.account.application.port.dto.OpenBankingToken;

/**
 * 오픈뱅킹 사용자 인증(3-legged OAuth) Port.
 *
 * <p>흐름은 이렇다.
 *
 * <pre>
 * 1. buildAuthorizationUrl() 로 만든 URL 로 사용자를 보낸다
 * 2. 사용자가 오픈뱅킹 인증 페이지에서 본인인증·계좌 연결에 동의한다
 * 3. Callback URL 로 인가 코드가 돌아온다
 * 4. exchangeCode() 로 코드를 액세스 토큰과 교환한다
 * </pre>
 *
 * <p>{@code state}는 CSRF 방지용이다. 1번에서 만든 값과 3번에서 돌아온 값이 같은지
 * 반드시 확인해야 한다. 확인하지 않으면 공격자가 자기 계좌를 피해자 계정에 연결할 수 있다.
 */
public interface OpenBankingAuthClient {

    /**
     * 사용자를 보낼 인증 페이지 URL을 만든다.
     *
     * @param state CSRF 방지용 난수. 콜백에서 대조한다
     */
    String buildAuthorizationUrl(String state);

    /** 콜백으로 받은 인가 코드를 토큰으로 교환한다. */
    OpenBankingToken exchangeCode(String authorizationCode);

    /** 만료된 액세스 토큰을 갱신한다. */
    OpenBankingToken refresh(String refreshToken);
}
