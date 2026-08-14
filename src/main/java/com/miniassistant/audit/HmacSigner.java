package com.miniassistant.audit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Детерминированная подпись HMAC-SHA256: один и тот же вход и ключ всегда
 * дают одну и ту же подпись (hex-строка, 64 символа). {@link AuditLog}
 * использует её, чтобы построить цепочку хешей, которую нельзя подделать без
 * знания ключа.
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final byte[] keyBytes;

    public HmacSigner(String key) {
        this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to compute HMAC-SHA256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(HEX_DIGITS[(b >> 4) & 0xF]);
            hex.append(HEX_DIGITS[b & 0xF]);
        }
        return hex.toString();
    }
}
