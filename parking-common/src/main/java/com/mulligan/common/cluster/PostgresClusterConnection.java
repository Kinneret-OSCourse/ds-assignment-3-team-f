package com.mulligan.common.cluster;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.security.TlsConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Cluster-aware Postgres connection helper. Builds a multi-host JDBC URL
 * pointing at the three Patroni replicas and asks the driver to always pick
 * the primary, falling over to whichever node is now the primary if the
 * previous one fails.
 *
 * <p>This replaces the single-host {@code DatabaseConnection} class that
 * shipped with Assignment 1 (one per module). The multi-host URL syntax
 * supported by the official Postgres JDBC driver is:
 * <pre>
 *   jdbc:postgresql://host1:5432,host2:5432,host3:5432/mulligan_db?targetServerType=primary
 * </pre>
 *
 * <p>If the operator points {@code MULLIGAN_DB_HOST} at HAProxy (the default
 * in our docker-compose) the multi-host URL still works because HAProxy is
 * just one more host.
 */
public final class PostgresClusterConnection {

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver not on classpath", e);
        }
    }

    private final String url;
    private final String user;
    private final String password;
    private final SecureLogger logger;

    public PostgresClusterConnection(SecureLogger logger) {
        this.logger = logger;
        String hostsRaw = resolve("mulligan.db.hosts", "MULLIGAN_DB_HOSTS");
        if (hostsRaw == null || hostsRaw.isBlank()) {
            String single = resolve("mulligan.db.host", "MULLIGAN_DB_HOST");
            if (single == null || single.isBlank()) {
                single = "localhost";
            }
            String port = resolve("mulligan.db.port", "MULLIGAN_DB_PORT");
            hostsRaw = (port == null || port.isBlank()) ? single + ":5432" : single + ":" + port;
        }
        ClusterEndpoints endpoints = ClusterEndpoints.parse(hostsRaw, null, 5432);
        String database = resolveOr("mulligan.db.name", "MULLIGAN_DB_NAME", "mulligan_db");

        StringBuilder builder = new StringBuilder("jdbc:postgresql://")
            .append(endpoints.asJdbcHostList())
            .append('/')
            .append(database)
            .append("?targetServerType=primary")
            .append("&connectTimeout=5")
            .append("&socketTimeout=30")
            .append("&tcpKeepAlive=true");
        builder.append(TlsConfig.jdbcTlsParameters());
        this.url = builder.toString();
        this.user = resolveOr("mulligan.db.user", "MULLIGAN_DB_USER", "mulligan_app");
        this.password = resolveOr("mulligan.db.password", "MULLIGAN_DB_PASSWORD", "mulligan_app_pw");

        if (logger != null) {
            logger.operational("DB cluster JDBC URL configured for " + endpoints);
        }
    }

    /**
     * Opens a new connection. Throws {@link SQLException} on cluster
     * exhaustion - the caller is expected to translate this into
     * {@code ClientErrorCodes.UNAVAILABLE} so internal driver messages never
     * leak.
     *
     * @return open JDBC connection to the current primary
     * @throws SQLException when no cluster member accepts the connection
     */
    public Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException sql) {
            if (logger != null) {
                logger.operational("DB connect failed: " + sql.getSQLState() + " " + sql.getMessage());
            }
            throw sql;
        }
    }

    /** Visible for tests. */
    public String jdbcUrl() {
        return url;
    }

    private static String resolveOr(String prop, String env, String fallback) {
        String value = resolve(prop, env);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String resolve(String prop, String env) {
        String value = System.getProperty(prop);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value;
    }
}
