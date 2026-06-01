package com.mulligan.common;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.MessageValidator;
import com.mulligan.common.security.NonceStore;
import com.mulligan.common.security.SecureMessage;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the Blue Team security primitives. The same test class
 * exercises HMAC signing, replay detection, timestamp expiry, and input
 * validation. These tests are the Assignment 2 §5.4 "security tests" entries.
 */
class SecurityLayerTest {

    private static final byte[] SECRET = new byte[]{
        (byte) 0xa3, 0x4b, 0x12, (byte) 0xff, 0x7e, 0x55, 0x21, 0x09,
        0x77, (byte) 0xaa, 0x10, 0x33, 0x44, 0x55, 0x66, 0x77,
        0x18, 0x29, 0x3a, 0x4b, 0x5c, 0x6d, 0x7e, (byte) 0x8f,
        (byte) 0x90, (byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4, (byte) 0xe5, (byte) 0xf6, 0x07
    };

    @Test
    void roundTripVerifies() {
        MessageSigner signer = new MessageSigner(SECRET);
        SecureMessage env = SecureMessage.wrap("{\"hello\":\"world\"}", signer);
        SecureMessage parsed = SecureMessage.fromJson(env.toJson());
        assertEquals(env.nonce, parsed.nonce);
        assertEquals(env.hmac, parsed.hmac);
        assertTrue(signer.verify(parsed.canonical(), parsed.hmac));
    }

    @Test
    void tamperedPayloadIsRejected() {
        MessageSigner signer = new MessageSigner(SECRET);
        SecureMessage env = SecureMessage.wrap("{\"amount\":10}", signer);
        String tampered = env.toJson().replace("\\\"amount\\\":10", "\\\"amount\\\":1000000");
        SecureMessage parsed = SecureMessage.fromJson(tampered);
        assertFalse(signer.verify(parsed.canonical(), parsed.hmac));
    }

    @Test
    void replayRejected() throws Exception {
        MessageSigner signer = new MessageSigner(SECRET);
        Path tmp = Files.createTempFile("sec-log-", ".log");
        SecureLogger log = newLogger("test", tmp);
        MessageValidator validator = new MessageValidator(signer, new NonceStore(), log);
        SecureMessage env = SecureMessage.wrap("{\"x\":1}", signer);
        SecureMessage copy1 = SecureMessage.fromJson(env.toJson());
        SecureMessage copy2 = SecureMessage.fromJson(env.toJson());
        assertTrue(validator.verify(copy1, "192.168.0.10").accepted);
        MessageValidator.Result second = validator.verify(copy2, "192.168.0.10");
        assertFalse(second.accepted);
        assertEquals("replay", second.reason);
    }

    @Test
    void staleTimestampRejected() throws Exception {
        MessageSigner signer = new MessageSigner(SECRET);
        Path tmp = Files.createTempFile("sec-log-", ".log");
        SecureLogger log = newLogger("test", tmp);
        MessageValidator validator = new MessageValidator(signer, new NonceStore(), log);

        // Build a manually-stale envelope by re-signing canonical with old TS.
        long staleTs = Instant.now().getEpochSecond() - (SecureMessage.MAX_AGE_SECONDS + 30);
        String nonce = java.util.UUID.randomUUID().toString();
        String canonical = SecureMessage.canonical(nonce, staleTs, "{}");
        String hmac = signer.sign(canonical);
        String json = String.format("{\"nonce\":\"%s\",\"timestamp\":%d,\"payload\":\"{}\",\"hmac\":\"%s\"}",
            nonce, staleTs, hmac);
        SecureMessage env = SecureMessage.fromJson(json);
        MessageValidator.Result result = validator.verify(env, "src");
        assertFalse(result.accepted);
        assertEquals("stale_timestamp", result.reason);
    }

    @Test
    void inputValidationRejectsBadPlate() {
        assertThrows(ValidationException.class, () -> InputValidator.requirePlate("not a plate!"));
        assertThrows(ValidationException.class, () -> InputValidator.requirePlate(""));
        assertEquals("123-45-678", InputValidator.requirePlate("123-45-678"));
    }

    @Test
    void inputValidationRejectsNegativeAmount() {
        assertThrows(ValidationException.class, () -> InputValidator.requireAmount(-1));
        assertThrows(ValidationException.class, () -> InputValidator.requireAmount(Double.NaN));
        assertEquals(12.5, InputValidator.requireAmount(12.5));
    }

    @Test
    void inputValidationCapsLongStrings() {
        String tooLong = "a".repeat(InputValidator.FREE_TEXT_MAX + 1);
        assertThrows(ValidationException.class, () -> InputValidator.requireFreeText(tooLong, InputValidator.FREE_TEXT_MAX));
    }

    private static SecureLogger newLogger(String component, Path tmp) throws Exception {
        // Use the package-private constructor by reflection to route the log to a temp file.
        java.lang.reflect.Constructor<SecureLogger> ctor =
            SecureLogger.class.getDeclaredConstructor(String.class, Path.class);
        ctor.setAccessible(true);
        return ctor.newInstance(component, tmp);
    }
}
