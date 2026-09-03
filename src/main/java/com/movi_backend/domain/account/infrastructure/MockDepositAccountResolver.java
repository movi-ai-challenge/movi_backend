package com.movi_backend.domain.account.infrastructure;

import com.movi_backend.domain.account.application.InternalAccountLocator;
import com.movi_backend.domain.account.entity.Account;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대역 이체에서 <b>받는 쪽이 우리 서비스 사용자인지</b> 찾는다.
 *
 * <p>실제 오픈뱅킹이라면 상대 은행이 입금하지만 대역에는 그 상대가 없다. 보내는 사람과 받는
 * 사람이 둘 다 우리 사용자인 시연에서는 받는 쪽 잔액도 늘어야 하는데, 그러려면 이체 명령의
 * 은행코드·계좌번호를 우리 {@code accounts} 의 핀테크이용번호로 바꿔야 한다.
 *
 * <p><b>은행코드와 전체 계좌번호가 정확히 같을 때만 찾는다.</b> 예전에는 마스킹되지 않은
 * 앞자리를 접두어로 맞췄는데, 앞 여섯 자리만 같은 남의 계좌에 입금될 수 있었다. 판단은
 * {@link InternalAccountLocator} 한 곳에 모아 본인 계좌 검사와 같은 규칙을 쓴다.
 *
 * <p>못 찾으면 입금을 건너뛴다. 이 시점에는 출금이 이미 끝났으므로 되돌리지 않는다 —
 * 나간 돈을 실패로 보고하면 사용자가 다시 보내 두 번 나간다. 대신 이체 요청을 만들 때
 * 이미 예금주조회로 계좌를 확인했으므로, 확인되지 않은 계좌로 출금만 나가지는 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDepositAccountResolver {

    private final InternalAccountLocator internalAccountLocator;

    @Transactional(readOnly = true)
    public Optional<String> resolveFintechUseNum(
            final String toBankCode,
            final String toAccountNum
    ) {
        final Optional<Account> account = internalAccountLocator.locate(
                toBankCode,
                digitsOf(toAccountNum)
        );
        if (account.isEmpty()) {
            log.debug("[MOCK-DEPOSIT] 받는 계좌가 우리 사용자의 계좌가 아니라 입금을 건너뜁니다.");
            return Optional.empty();
        }
        return Optional.ofNullable(account.get().getFintechUseNum());
    }

    private String digitsOf(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }
}
