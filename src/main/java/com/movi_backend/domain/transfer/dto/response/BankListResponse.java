package com.movi_backend.domain.transfer.dto.response;

import com.movi_backend.domain.transfer.application.BankDirectory;
import java.util.List;

/**
 * 상대방을 등록할 때 고를 수 있는 은행 목록.
 *
 * <p>계좌번호 앞자리로 은행을 추정하지 않기로 했으므로 사용자가 직접 고른다. 그 선택지를
 * 화면이 따로 적어 두면 코드가 갈리므로 백엔드가 준다.
 */
public record BankListResponse(int totalCount, List<Bank> banks) {

    public static BankListResponse from(final List<BankDirectory.Bank> banks) {
        return new BankListResponse(
                banks.size(),
                banks.stream().map(bank -> new Bank(bank.code(), bank.name())).toList()
        );
    }

    public record Bank(String code, String name) {
    }
}
