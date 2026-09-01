package com.movi_backend.domain.account.application.port;

import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferCommand;
import com.movi_backend.domain.account.application.port.dto.OpenBankingTransferResult;

/**
 * 실제 출금이 일어나는 경계.
 *
 * <p>계좌 연결·계좌목록({@code movi.openbanking.mode})과 따로 껐다 켤 수 있게 이체만 떼어냈다.
 * 오픈뱅킹 출금이체 API는 사업자 등록을 마친 이용기관에만 열려서, 연결까지는 샌드박스로
 * 진행해도 이체는 대역을 써야 하는 구간이 생긴다. 잔액조회를 {@link BalanceInquiryPort}로
 * 가른 것과 같은 이유다.
 */
public interface OpenBankingTransferPort {

    OpenBankingTransferResult transfer(OpenBankingTransferCommand command, String accessToken);
}
