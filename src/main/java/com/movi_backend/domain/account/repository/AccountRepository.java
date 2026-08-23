package com.movi_backend.domain.account.repository;

import com.movi_backend.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserIdAndPrimaryTrue(Long userId);

    Optional<Account> findByUserIdAndAlias(Long userId, String alias);

    /** 홈 진입·계좌 목록 조회용 */
    List<Account> findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(Long userId);

    /** 핀테크이용번호는 계좌의 실질 식별자다. 재연결 시 중복 생성을 막는다 */
    Optional<Account> findByFintechUseNum(String fintechUseNum);

    /** 소유권 검증까지 한 번에 — 남의 계좌를 건드리지 못하게 한다 */
    Optional<Account> findByIdAndUserId(Long accountId, Long userId);
}
