package com.movi_backend.domain.account.infrastructure.openbanking;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 거래고유번호(bank_tran_id) 생성기.
 *
 * <p>형식은 <b>이용기관코드(10자리) + 구분자 U + 임의 9자리</b> = 총 20자다.
 * 이용기관코드는 오픈뱅킹이 토큰 응답의 {@code client_use_code}로 알려준다.
 * 테스트베드에서 받은 값이 {@code M202602152}로 10자리임을 확인했다.
 *
 * <p>이 값은 오픈뱅킹 쪽 거래 식별자이므로 <b>요청마다 새로 만들어야 한다.</b>
 * 다만 이체 재시도처럼 같은 거래를 다시 보내는 경우에는 앞서 쓴 값을 그대로
 * 재사용해야 중복 이체가 나가지 않는다.
 */
@Component
public class BankTranIdGenerator {

    private static final String SEPARATOR = "U";
    private static final int SEQUENCE_LENGTH = 9;
    private static final String DIGITS = "0123456789";

    private final SecureRandom random = new SecureRandom();

    public String generate(final String clientUseCode) {
        final StringBuilder sequence = new StringBuilder(SEQUENCE_LENGTH);
        for (int index = 0; index < SEQUENCE_LENGTH; index++) {
            sequence.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return clientUseCode + SEPARATOR + sequence;
    }
}
