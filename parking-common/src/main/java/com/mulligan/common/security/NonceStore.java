package com.mulligan.common.security;

import com.mulligan.common.cluster.PostgresClusterConnection;
import com.mulligan.common.logging.SecureLogger;

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
    private final boolean jdbcBacked;
    private final SecureLogger log;
    private volatile PostgresClusterConnection nonceDb;
    private volatile boolean nonceTableReady;

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
        this.jdbcBacked = "jdbc".equalsIgnoreCase(resolve("mulligan.nonce.store", "MULLIGAN_NONCE_STORE"));
        this.log = new SecureLogger("nonce-store");
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
        if (jdbcBacked) {
            return registerIfFreshInDatabase(nonce);
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

    private boolean registerIfFreshInDatabase(String nonce) {
        try (var conn = nonceConnection()) {
            ensureNonceTable(conn);
            conn.setAutoCommit(false);
            try (var delete = conn.prepareStatement(
                    "DELETE FROM security_nonces WHERE first_seen < CURRENT_TIMESTAMP - (? * INTERVAL '1 second')")) {
                delete.setLong(1, ttl.toSeconds());
                delete.executeUpdate();
            }
            try (var insert = conn.prepareStatement(
                    "INSERT INTO security_nonces (nonce, first_seen) VALUES (?, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING")) {
                insert.setString(1, nonce);
                int inserted = insert.executeUpdate();
                conn.commit();
                return inserted == 1;
            } catch (Exception e) {
                conn.rollback();
                if ("23505".equals(sqlState(e))) {
                    return false;
                }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            log.security("REJECT nonce-store-unavailable err=" + e.getClass().getSimpleName());
            return false;
        }
    }

    private java.sql.Connection nonceConnection() throws java.sql.SQLException {
        if (nonceDb == null) {
            synchronized (this) {
                if (nonceDb == null) {
                    nonceDb = new PostgresClusterConnection(log);
                }
            }
        }
        return nonceDb.getConnection();
    }

    private void ensureNonceTable(java.sql.Connection conn) throws java.sql.SQLException {
        if (nonceTableReady) {
            return;
        }
        synchronized (this) {
            if (nonceTableReady) {
                return;
            }
            try (var check = conn.prepareStatement("SELECT to_regclass('public.security_nonces')")) {
                try (var rs = check.executeQuery()) {
                    if (rs.next() && rs.getString(1) != null) {
                        nonceTableReady = true;
                        return;
                    }
                }
            }
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS security_nonces (
                            nonce VARCHAR(128) PRIMARY KEY,
                            first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_security_nonces_first_seen ON security_nonces(first_seen)");
            }
            nonceTableReady = true;
        }
    }

    private String sqlState(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof java.sql.SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return "";
    }

    private static String resolve(String prop, String env) {
        String value = System.getProperty(prop);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value;
    }
}
