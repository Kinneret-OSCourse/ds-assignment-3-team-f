package com.mulligan.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory set of recently observed message nonces. Used by the queue server
 * (and any consumer that wants strict at-most-once semantics) to detect
 * replays of legitimate messages.
 *
 * <p>Entries automatically expire after the configured TTL so memory stays
 * bounded. The default TTL matches {@link SecureMessage#MAX_AGE_SECONDS} since
 * a message older than that would already be rejected by the timestamp check.
 *
 * <p>This is the Blue Team mitigation for the "Replay-style message
 * duplication" attack class.
 */
public final class NonceStore {

    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final AtomicLong sweepCounter = new AtomicLong();

    /** Creates a nonce store with the default {@link SecureMessage#MAX_AGE_SECONDS} TTL. */
    public NonceStore() {
        this(Duration.ofSeconds(SecureMessage.MAX_AGE_SECONDS));
    }

    /**
     * @param ttl how long observed nonces are retained
     */
    public NonceStore(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        this.ttl = ttl;
    }

    /**
     * Records a nonce as seen.
     *
     * @param nonce envelope nonce
     * @return {@code true} if this nonce had not been seen before, {@code false} if it is a replay
     */
    public boolean registerIfFresh(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        // Cheap incremental sweep so the store does not need a background thread.
        if (sweepCounter.incrementAndGet() % 64 == 0) {
            sweep(now);
        }
        Long existing = seen.putIfAbsent(nonce, now);
        if (existing == null) {
            return true;
        }
        if (now - existing > ttl.toMillis()) {
            // Stale entry - replace it. This is a slightly soft check; we treat
            // a re-use after TTL as a fresh nonce because the timestamp check
            // will independently reject anything older than 60s.
            seen.put(nonce, now);
            return true;
        }
        return false;
    }

    /** Test hook: forget all nonces. */
    public void clear() {
        seen.clear();
    }

    /** @return number of nonces currently stored (mainly for diagnostics). */
    public int size() {
        return seen.size();
    }

    private void sweep(long nowMillis) {
        long cutoff = nowMillis - ttl.toMillis();
        seen.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
