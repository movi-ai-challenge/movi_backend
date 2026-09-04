package com.movi_backend.domain.account.application.port;

import com.movi_backend.domain.account.application.port.dto.VerifiedAccountHolder;
import java.util.Optional;

/**
 * 수취 계좌의 예금주를 조회한다. 오픈뱅킹 예금주조회에 대응한다.
 *
 * <p><b>이 포트가 이 프로젝트에서 유일한 계좌 검증 근거다.</b> 예전에는 우리
 * {@code accounts.account_num_masked} 의 앞자리를 접두어로 맞춰 "실재하는 계좌"라고 판단했는데,
 * 마스킹된 값에는 전체 번호가 없어 앞 여섯 자리만 같으면 전혀 다른 계좌가 통과했다. 그렇게
 * 저장된 수취인은 이름으로 부르는 순간 남에게 돈을 보낸다.
 *
 * <p>구현은 <b>정확히 일치</b>할 때만 결과를 준다. 부분 일치·추정·보정을 하지 않는다.
 *
 * <p>조회할 수단이 없으면 {@link Optional#empty()}를 돌려준다. 호출자는 이것을 "검증되지
 * 않음"으로 다루고 이체를 진행하지 않는다 — FDS 와 같은 fail-closed 규칙이다.
 */
public interface AccountHolderInquiryPort {

    /**
     * @param bankCode      은행 코드
     * @param accountNumber 숫자만 남긴 전체 계좌번호
     * @return 확인된 예금주. 확인할 수 없으면 비어 있다
     */
    Optional<VerifiedAccountHolder> inquire(String bankCode, String accountNumber);
}
