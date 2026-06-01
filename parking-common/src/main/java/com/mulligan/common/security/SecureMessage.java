package com.mulligan.common.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope that carries a signed, replay-protected payload over RabbitMQ.
 *
 * <p>Wire format (JSON object, field order is deterministic so the HMAC is
 * stable):
 * <pre>{@code
 * {
 *   "nonce":     "<UUID v4>",
 *   "timestamp": <unix seconds>,
 *   "payload":   "<inner JSON>",
 *   "hmac":      "<lowercase hex HMAC-SHA256 over canonical>"
 * }
 * }</pre>
 *
 * <p>The HMAC covers the canonical string {@code nonce + "|" + timestamp + "|"
 * + payload}, which is what {@link #canonical(String, long, String)} returns.
 * Both producers and consumers must use this class so that ordering and field
 * names cannot drift.
 *
 * <p>This is the primary Blue Team mitigation against:
 * <ul>
 *   <li>RabbitMQ fake message injection (HMAC catches forged payloads).</li>
 *   <li>Replay-style message duplication (nonce + timestamp + server-side
 *       {@link NonceStore} reject repeated or stale messages).</li>
 * </ul>
 */
public final class SecureMessage {

    /** Maximum acceptable age of a message in seconds. */
    public static final long MAX_AGE_SECONDS = 60L;

    public final String nonce;
    public final long timestampUnixSeconds;
    public final String payload;
    public final String hmac;

    private SecureMessage(String nonce, long timestamp, String payload, String hmac) {
        this.nonce = nonce;
        this.timestampUnixSeconds = timestamp;
        this.payload = payload;
        this.hmac = hmac;
    }

    /**
     * Wraps an inner payload in a freshly-signed envelope.
     *
     * @param payload the inner JSON or text payload (must not be null)
     * @param signer  HMAC signer used to authenticate the envelope
     * @return a new envelope with random nonce and current timestamp
     */
    public static SecureMessage wrap(String payload, MessageSigner signer) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (signer == null) {
            throw new IllegalArgumentException("signer must not be null");
        }
        String nonce = UUID.randomUUID().toString();
        long ts = Instant.now().getEpochSecond();
        String canonical = canonical(nonce, ts, payload);
        return new SecureMessage(nonce, ts, payload, signer.sign(canonical));
    }

    /** @return the deterministic JSON wire representation. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nonce", nonce);
        map.put("timestamp", timestampUnixSeconds);
        map.put("payload", payload);
        map.put("hmac", hmac);
        return JsonWriter.write(map);
    }

    /**
     * Parses a JSON envelope produced by {@link #toJson()}. This method only
     * deserialises - it does not verify. Use {@link MessageValidator} for the
     * full verification + replay check.
     *
     * @param json wire JSON to parse
     * @return parsed envelope
     * @throws IllegalArgumentException if the JSON is malformed or fields are missing
     */
    public static SecureMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty envelope");
        }
        Map<String, String> fields = JsonReader.flatRead(json);
        String nonce = fields.get("nonce");
        String tsRaw = fields.get("timestamp");
        String payload = fields.get("payload");
        String hmac = fields.get("hmac");
        if (nonce == null || tsRaw == null || payload == null || hmac == null) {
            throw new IllegalArgumentException("Missing required envelope field");
        }
        long ts;
        try {
            ts = Long.parseLong(tsRaw);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
        return new SecureMessage(nonce, ts, payload, hmac);
    }

    /**
     * @param nonce envelope nonce
     * @param timestamp envelope timestamp (unix seconds)
     * @param payload inner payload
     * @return the canonical string the HMAC covers
     */
    public static String canonical(String nonce, long timestamp, String payload) {
        return nonce + "|" + timestamp + "|" + payload;
    }

    /** @return the canonical string for this envelope. */
    public String canonical() {
        return canonical(nonce, timestampUnixSeconds, payload);
    }
}
