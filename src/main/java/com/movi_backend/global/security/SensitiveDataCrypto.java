package com.movi_backend.global.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataCrypto {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int MINIMUM_HASH_KEY_LENGTH_BYTES = 32;

    private final SecretKey encryptionKey;
    private final SecretKey hashKey;
    private final SecureRandom secureRandom;

    public SensitiveDataCrypto(final CryptoProperties properties) {
        final byte[] encryptionKeyBytes = decodeKey(properties.encryptionKey(), "암호화 키");
        validateEncryptionKeyLength(encryptionKeyBytes);
        final byte[] hashKeyBytes = decodeKey(properties.hashKey(), "해시 키");
        if (hashKeyBytes.length < MINIMUM_HASH_KEY_LENGTH_BYTES) {
            throw new IllegalStateException("민감정보 해시 키는 32바이트 이상이어야 합니다.");
        }
        this.encryptionKey = new SecretKeySpec(encryptionKeyBytes, AES_ALGORITHM);
        this.hashKey = new SecretKeySpec(hashKeyBytes, HASH_ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(final String plainText) {
        try {
            final byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(join(iv, encrypted));
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("민감정보를 암호화하지 못했습니다.", exception);
        }
    }

    public String decrypt(final String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalStateException("민감정보를 복호화하지 못했습니다.");
        }
        try {
            final byte[] combined = Base64.getDecoder().decode(encryptedText);
            if (combined.length <= IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / Byte.SIZE) {
                throw new IllegalArgumentException("암호문 길이가 유효하지 않습니다.");
            }
            final byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            final byte[] encrypted = Arrays.copyOfRange(
                    combined,
                    IV_LENGTH_BYTES,
                    combined.length
            );
            final Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("민감정보를 복호화하지 못했습니다.", exception);
        }
    }

    public String hash(final String value) {
        try {
            final Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(hashKey);
            final byte[] hashed = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("민감정보 검색 해시를 만들지 못했습니다.", exception);
        }
    }

    private static byte[] decodeKey(final String encodedKey, final String keyName) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(keyName + " 설정이 필요합니다.");
        }
        try {
            return Base64.getDecoder().decode(encodedKey);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException(keyName + "는 Base64 형식이어야 합니다.", exception);
        }
    }

    private static void validateEncryptionKeyLength(final byte[] key) {
        if (key.length == 16 || key.length == 24 || key.length == 32) {
            return;
        }
        throw new IllegalStateException("AES 암호화 키는 16, 24, 32바이트 중 하나여야 합니다.");
    }

    private static byte[] join(final byte[] first, final byte[] second) {
        return ByteBuffer.allocate(first.length + second.length)
                .put(first)
                .put(second)
                .array();
    }
}
