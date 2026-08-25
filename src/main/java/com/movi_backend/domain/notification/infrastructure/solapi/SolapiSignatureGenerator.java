package com.movi_backend.domain.notification.infrastructure.solapi;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 솔라피 요청 서명 생성.
 *
 * <p>솔라피는 {@code date}(요청 시각) + {@code salt}(요청마다 새로 뽑는 난수)를
 * API Secret으로 HMAC-SHA256 서명해 {@code Authorization} 헤더에 담을 것을 요구한다.
 * salt를 재사용하면 재전송 공격에 노출되므로 매 호출마다 새로 생성한다.
 */
@Component
public class SolapiSignatureGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SALT_BYTE_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    SolapiSignature generate(final String apiSecret) {
        final String date = Instant.now().toString();
        final String salt = generateSalt();
        final String signature = sign(date + salt, apiSecret);
        return new SolapiSignature(date, salt, signature);
    }

    private String generateSalt() {
        final byte[] saltBytes = new byte[SALT_BYTE_LENGTH];
        secureRandom.nextBytes(saltBytes);
        return HexFormat.of().formatHex(saltBytes);
    }

    private String sign(final String data, final String apiSecret) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            final byte[] signed = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed);
        } catch (final NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("솔라피 요청 서명 생성에 실패했습니다.", exception);
        }
    }
}
