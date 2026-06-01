package com.mulligan.common.security;

/**
 * Generic error codes returned to UI/CLI clients. Internal stack traces or
 * driver-specific messages are <strong>never</strong> propagated to the client
 * — they are only written to the secure log on the server side. This is the
 * Blue Team mitigation for "Internal errors and stack traces exposed to
 * users".
 */
public final class ClientErrorCodes {

    /** Returned for any database, JDBC, or back-end failure. */
    public static final String INTERNAL = "ERR_INTERNAL";

    /** Returned when a request fails server-side input validation. */
    public static final String INVALID_INPUT = "ERR_INVALID_INPUT";

    /** Returned when authentication fails (bad credentials). */
    public static final String AUTH = "ERR_AUTH";

    /** Returned when an authenticated user is not allowed to perform an action. */
    public static final String FORBIDDEN = "ERR_FORBIDDEN";

    /** Returned when an envelope fails the HMAC, replay, or timestamp checks. */
    public static final String SECURITY = "ERR_SECURITY";

    /** Returned when a downstream service (queue or DB cluster) is unreachable. */
    public static final String UNAVAILABLE = "ERR_UNAVAILABLE";

    private ClientErrorCodes() {
    }
}
