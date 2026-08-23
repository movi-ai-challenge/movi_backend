package com.movi_backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveDataCryptoTest {

    private static final CryptoProperties PROPERTIES = new CryptoProperties(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    );

    @Test
    @DisplayName("같은 전화번호를 암호화하면 매번 다른 암호문을 반환한다")
    void 같은_전화번호의_암호문은_매번_달라진다() {
        // given
        final SensitiveDataCrypto crypto = new SensitiveDataCrypto(PROPERTIES);

        // when
        final String first = crypto.encrypt("01012345678");
        final String second = crypto.encrypt("01012345678");

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("01012345678");
        assertThat(second).doesNotContain("01012345678");
    }

    @Test
    @DisplayName("같은 전화번호의 검색 해시는 항상 동일하다")
    void 같은_전화번호의_검색_해시는_동일하다() {
        // given
        final SensitiveDataCrypto crypto = new SensitiveDataCrypto(PROPERTIES);

        // when
        final String first = crypto.hash("01012345678");
        final String second = crypto.hash("01012345678");

        // then
        assertThat(first).isEqualTo(second);
        assertThat(first).doesNotContain("01012345678");
    }
}
