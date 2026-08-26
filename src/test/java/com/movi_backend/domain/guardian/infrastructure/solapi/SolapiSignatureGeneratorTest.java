package com.movi_backend.domain.guardian.infrastructure.solapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolapiSignatureGeneratorTest {

    private static final String API_SECRET = "test-api-secret";

    private final SolapiSignatureGenerator generator = new SolapiSignatureGenerator();

    @Test
    @DisplayName("서명은 date·salt·signature를 모두 채운다")
    void 서명은_date_salt_signature를_모두_채운다() {
        // when
        final SolapiSignature signature = generator.generate(API_SECRET);

        // then
        assertThat(signature.date()).isNotBlank();
        assertThat(signature.salt()).isNotBlank();
        assertThat(signature.signature()).hasSize(64); // HMAC-SHA256 hex = 32바이트 * 2
    }

    @Test
    @DisplayName("호출할 때마다 salt가 달라진다")
    void 호출할_때마다_salt가_달라진다() {
        // when
        final SolapiSignature first = generator.generate(API_SECRET);
        final SolapiSignature second = generator.generate(API_SECRET);

        // then
        assertThat(first.salt()).isNotEqualTo(second.salt());
        assertThat(first.signature()).isNotEqualTo(second.signature());
    }

    @Test
    @DisplayName("Authorization 헤더는 솔라피 HMAC-SHA256 규격을 따른다")
    void Authorization_헤더는_솔라피_규격을_따른다() {
        // given
        final SolapiSignature signature = generator.generate(API_SECRET);

        // when
        final String header = signature.toAuthorizationHeader("test-api-key");

        // then
        assertThat(header)
                .startsWith("HMAC-SHA256 apiKey=test-api-key, date=")
                .contains("salt=" + signature.salt())
                .contains("signature=" + signature.signature());
    }
}
