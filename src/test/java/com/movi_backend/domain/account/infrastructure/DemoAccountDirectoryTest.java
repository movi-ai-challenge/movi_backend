package com.movi_backend.domain.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoAccountDirectoryTest {

    private final DemoAccountDirectory directory = new DemoAccountDirectory();

    @Test
    @DisplayName("은행코드와 전체 계좌번호가 모두 같아야 찾는다")
    void 정확히_같을_때만_찾는다() {
        // when
        final var found = directory.find(
                DemoAccountDirectory.RECIPIENT_MOTHER.bankCode(),
                DemoAccountDirectory.RECIPIENT_MOTHER.accountNumber()
        );

        // then
        assertThat(found).isPresent();
        assertThat(found.get().holderName()).isEqualTo("이영자");
    }

    @Test
    @DisplayName("앞자리만 같고 뒷자리가 다른 계좌번호는 찾지 않는다")
    void 앞자리만_같으면_찾지_않는다() {
        // given — 110123456789 는 있지만 110123450000 은 없다
        final String samePrefix = "110123450000";

        // when & then
        assertThat(directory.find("088", samePrefix)).isEmpty();
    }

    @Test
    @DisplayName("한 자리만 다른 계좌번호도 찾지 않는다")
    void 한_자리_다르면_찾지_않는다() {
        assertThat(directory.find("088", "110123456788")).isEmpty();
    }

    @Test
    @DisplayName("계좌번호가 같아도 은행이 다르면 다른 계좌다")
    void 은행이_다르면_다른_계좌다() {
        // given — 엄마 계좌는 신한은행(088) 이다
        final String motherAccount = DemoAccountDirectory.RECIPIENT_MOTHER.accountNumber();

        // when & then
        assertThat(directory.find("004", motherAccount)).isEmpty();
        assertThat(directory.find("088", motherAccount)).isPresent();
    }

    @Test
    @DisplayName("우리 사용자의 계좌에는 입금 대상 핀테크이용번호가 있다")
    void 우리_계좌는_입금_대상을_안다() {
        // when
        final var found = directory.find(
                DemoAccountDirectory.DEMO_USER_CHECKING.bankCode(),
                DemoAccountDirectory.DEMO_USER_CHECKING.accountNumber()
        );

        // then
        assertThat(found).isPresent();
        assertThat(found.get().fintechUseNum()).isEqualTo("199000000000000000000001");
    }

    @Test
    @DisplayName("외부 수취인은 입금 대상이 없다 - 우리가 잔액을 들고 있지 않다")
    void 외부_수취인은_입금_대상이_없다() {
        // when
        final var found = directory.find(
                DemoAccountDirectory.RECIPIENT_SON.bankCode(),
                DemoAccountDirectory.RECIPIENT_SON.accountNumber()
        );

        // then
        assertThat(found).isPresent();
        assertThat(found.get().fintechUseNum()).isNull();
    }
}
