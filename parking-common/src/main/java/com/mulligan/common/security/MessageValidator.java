package com.mulligan.common.security;

import com.mulligan.common.logging.SecureLogger;

import java.time.Instant;

/**
 * Server-side helper that performs the full Blue Team verification for an
 * incoming {@link SecureMessage}:
 * <ol>
 *   <li>HMAC signature must verify against the shared secret.</li>
 *   <li>Timestamp must be no more than {@link SecureMessage#MAX_AGE_SECONDS}
 *       seconds in the past and no more than the configured clock-skew window
 *       in the future.</li>
 *   <li>Nonce must not have been seen before within the TTL window.</li>
 * </ol>
 *
 * <p>Every rejection is logged via {@link SecureLogger#security} so the
 * operator gets a persistent audit trail of suspect traffic, including the
 * source identifier supplied by the caller (typically the AMQP connection's
 * peer host).
 */
public final class MessageValidator {

    /** A small allowance for clock skew between hosts. */
    public static final long CLOCK_SKEW_TOLERANCE_SECONDS = 5L;

    private final MessageSigner signer;
    private final NonceStore nonces;
    private final SecureLogger log;

    public MessageValidator(MessageSigner signer, NonceStore nonces, SecureLogger log) {
        if (signer == null || nonces == null || log == null) {
            throw new IllegalArgumentException("signer, nonces, log must all be non-null");
        }
        this.signer = signer;
        this.nonces = nonces;
        this.log = log;
    }

    /**
     * Performs the full check.
     *
     * @param envelope deserialised secure envelope
     * @param sourceAddress logical source (e.g. AMQP peer host) for audit logs
     * @return a {@link Result} describing whether the message is accepted
     */
    public Result verify(SecureMessage envelope, String sourceAddress) {
        if (envelope == null) {
            log.security("REJECT empty-envelope source=" + sourceAddress);
            return Result.reject("empty");
        }
        if (!signer.verify(envelope.canonical(), envelope.hmac)) {
            log.security("REJECT bad-hmac nonce=" + envelope.nonce + " source=" + sourceAddress);
            return Result.reject("bad_hmac");
        }
        long now = Instant.now().getEpochSecond();
        long ageSeconds = now - envelope.timestampUnixSeconds;
        if (ageSeconds > SecureMessage.MAX_AGE_SECONDS) {
            log.security("REJECT stale-timestamp age=" + ageSeconds + " nonce=" + envelope.nonce
                + " source=" + sourceAddress);
            return Result.reject("stale_timestamp");
        }
        if (ageSeconds < -CLOCK_SKEW_TOLERANCE_SECONDS) {
            log.security("REJECT future-timestamp skew=" + (-ageSeconds) + " nonce=" + envelope.nonce
                + " source=" + sourceAddress);
            return Result.reject("future_timestamp");
        }
        if (!nonces.registerIfFresh(envelope.nonce)) {
            log.security("REJECT replay nonce=" + envelope.nonce + " source=" + sourceAddress);
            return Result.reject("replay");
        }
        return Result.accept();
    }

    /** Result of {@link #verify(SecureMessage, String)}. */
    public static final class Result {
        public final boolean accepted;
        public final String reason;

        private Result(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason;
        }

        public static Result accept() {
            return new Result(true, "ok");
        }

        public static Result reject(String reason) {
            return new Result(false, reason);
        }
    }
}
