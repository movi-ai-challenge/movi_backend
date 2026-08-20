package com.movi_backend.domain.account.application.port;

import com.movi_backend.domain.account.application.port.dto.OpenBankingAccount;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;
import java.util.List;

/**
 * 오픈뱅킹 연동 Port — 계좌 목록 조회와 이체 실행.
 *
 * <p>도메인은 이 인터페이스에만 의존하고 실제 통신 방식은 모른다. Sandbox 승인 전에는 Mock
 * 어댑터로 개발하고, 승인 후 HTTP 어댑터로 교체한다. 교체는 설정
 * {@code movi.openbanking.mode} 하나로 이뤄지며 서비스 코드는 바뀌지 않는다.
 *
 * <p><b>잔액조회는 이 Port가 아니라 {@link BalanceInquiryPort}가 담당한다.</b>
 * 잔액은 호출 빈도와 캐시 정책이 달라 별도 Port로 분리했다.
 *
 * <p>구현체는 실패를 {@code BusinessException}으로 변환해서 던진다. 서비스가 오픈뱅킹의
 * 응답 코드를 직접 해석하지 않게 하기 위함이다.
 */
public interface OpenBankingClient {

    /**
     * 사용자가 연결한 계좌 목록을 조회한다.
     *
     * @param userSeqNo   금결원 사용자일련번호
     * @param accessToken 사용자 인증으로 발급받은 토큰. 저장 시 암호화 대상이므로
     *                    로그에 남기지 않는다
     */
    List<OpenBankingAccount> fetchAccounts(String userSeqNo, String accessToken);

    /**
     * 이체를 실행한다.
     *
     * <p>{@code command.tranId()}로 중복 실행을 차단한다. 같은 키로 다시 호출하면
     * 새 이체를 만들지 않고 기존 결과를 반환한다.
     *
     * @param accessToken 사용자 인증으로 발급받은 토큰
     */
    OpenBankingTransferResult transfer(OpenBankingTransferCommand command, String accessToken);
}
