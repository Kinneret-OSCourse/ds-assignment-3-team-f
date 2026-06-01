package com.mulligan.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produces and verifies HMAC-SHA256 signatures for queue and inter-service
 * messages. The shared secret is loaded from {@code MULLIGAN_HMAC_KEY} (or the
 * {@code mulligan.hmac.key} system property) so the key never ships in source.
 *
 * <p>Defense fix for the Red Team attack "RabbitMQ fake message injection":
 * any consumer that uses {@link #verify(String, String)} can detect tampered
 * or forged payloads because they will not carry a valid HMAC tag.
 */
public final class MessageSigner {

    /** HMAC-SHA256 produces 32 raw bytes / 64 hex characters. */
    public static final int SIGNATURE_LENGTH_HEX = 64;

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_PROPERTY = "mulligan.hmac.key";
    private static final String SECRET_ENV = "MULLIGAN_HMAC_KEY";
    private static final String DEV_SECRET =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final byte[] secret;

    /**
     * Creates a signer using the secret resolved from system property or
     * environment variable. The constructor fails fast if no secret has been
     * configured so misconfiguration never silently degrades to "no HMAC".
     */
    public MessageSigner() {
        this(resolveSecret());
    }

    /**
     * Creates a signer using an explicit secret. Primarily used in tests.
     *
     * @param secret raw secret bytes, must be non-null and at least 32 bytes
     */
    public MessageSigner(byte[] secret) {
        Objects.requireNonNull(secret, "HMAC secret must not be null");
        if (secret.length < 32) {
            throw new IllegalArgumentException(
                "HMAC secret must be at least 32 bytes; configure MULLIGAN_HMAC_KEY with a strong key");
        }
        this.secret = secret.clone();
    }

    /**
     * Signs the supplied UTF-8 payload and returns the lowercase hex tag.
     *
     * @param payload the canonical payload to authenticate (must not be null)
     * @return 64 character lowercase hex HMAC-SHA256 tag
     */
    public String sign(String payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] tag = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(tag);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verifies a signature using a constant-time comparison.
     *
     * @param payload the canonical payload that was signed
     * @param providedHex the candidate hex HMAC supplied by the caller
     * @return {@code true} iff the candidate matches the expected HMAC byte for byte
     */
    public boolean verify(String payload, String providedHex) {
        if (payload == null || providedHex == null) {
            return false;
        }
        if (providedHex.length() != SIGNATURE_LENGTH_HEX) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(sign(payload));
            byte[] provided = HexFormat.of().parseHex(providedHex.toLowerCase());
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }
    }

    private static byte[] resolveSecret() {
        String raw = System.getProperty(SECRET_PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(SECRET_ENV);
        }
        if (raw == null || raw.isBlank()) {
            raw = DEV_SECRET;
        }
        // Allow either a raw secret or a hex-encoded secret. Hex strings of even
        // length and length >= 64 are decoded; everything else is used as UTF-8.
        if (raw.length() >= 64 && raw.length() % 2 == 0 && raw.matches("[0-9a-fA-F]+")) {
            return HexFormat.of().parseHex(raw);
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
