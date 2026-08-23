package com.movi_backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(crypto.decrypt(first)).isEqualTo("01012345678");
        assertThat(crypto.decrypt(second)).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화를 거부한다")
    void 변조된_암호문은_복호화할_수_없다() {
        final SensitiveDataCrypto crypto = new SensitiveDataCrypto(PROPERTIES);
        final String encrypted = crypto.encrypt("access-token");
        final char replacement = encrypted.charAt(encrypted.length() - 2) == 'A' ? 'B' : 'A';
        final String tampered = encrypted.substring(0, encrypted.length() - 2)
                + replacement
                + encrypted.substring(encrypted.length() - 1);

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("민감정보를 복호화하지 못했습니다.");
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
