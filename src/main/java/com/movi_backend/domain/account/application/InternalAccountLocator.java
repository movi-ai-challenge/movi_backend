package com.movi_backend.domain.account.application;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.infrastructure.DemoAccountDirectory;
import com.movi_backend.domain.account.repository.AccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어떤 계좌가 <b>우리 서비스에 연결된 계좌</b>인지 찾는다.
 *
 * <p>두 곳에서 쓴다 — 본인 계좌로 보내려는 것을 막을 때, 그리고 대역 이체가 입금할 대상을
 * 찾을 때다. 두 판단이 같은 규칙을 써야 한쪽만 통과하는 구멍이 생기지 않는다.
 *
 * <p><b>정확히 일치할 때만 찾는다.</b> {@code accounts.account_num_masked} 에는 전체 번호가
 * 없어 예전에는 마스킹되지 않은 앞자리를 접두어로 맞췄는데, 앞 여섯 자리만 같은 전혀 다른
 * 계좌가 걸렸다. 전체 번호를 아는 곳은 {@link DemoAccountDirectory}(시연) 또는 은행(실제)
 * 뿐이므로, 거기서 얻은 핀테크이용번호로 우리 계좌를 찾는다.
 *
 * <p><b>실제 모드에서는 항상 비어 있다.</b> 명부가 빈으로 올라오지 않기 때문이다. 그때는
 * 예금주조회 자체가 실패해 이체가 진행되지 않으므로, 검사를 건너뛴 채로 돈이 나가지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InternalAccountLocator {

    /** 실제 모드에서는 비어 있다. */
    private final ObjectProvider<DemoAccountDirectory> demoAccountDirectory;

    private final AccountRepository accountRepository;

    /**
     * 은행코드와 전체 계좌번호로 우리 계좌를 찾는다.
     *
     * @param bankCode      은행 코드
     * @param accountNumber 숫자만 남긴 전체 계좌번호
     * @return 해지되지 않은 우리 계좌. 우리 사용자의 계좌가 아니면 비어 있다
     */
    @Transactional(readOnly = true)
    public Optional<Account> locate(final String bankCode, final String accountNumber) {
        return findFintechUseNum(bankCode, accountNumber)
                .flatMap(accountRepository::findByFintechUseNum)
                .filter(Account::isActive);
    }

    private Optional<String> findFintechUseNum(
            final String bankCode,
            final String accountNumber
    ) {
        final DemoAccountDirectory directory = demoAccountDirectory.getIfAvailable();
        if (directory == null) {
            return Optional.empty();
        }
        return directory.find(bankCode, accountNumber)
                .map(DemoAccountDirectory.DemoAccount::fintechUseNum);
    }
}
