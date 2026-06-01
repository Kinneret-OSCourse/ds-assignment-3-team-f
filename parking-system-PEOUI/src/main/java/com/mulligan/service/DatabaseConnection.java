package com.mulligan.service;

import com.mulligan.common.cluster.PostgresClusterConnection;
import com.mulligan.common.logging.SecureLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides JDBC connections for the PEO module against the 3-node Patroni
 * Postgres cluster.
 *
 * <p>The cluster-aware URL construction lives in
 * {@link PostgresClusterConnection} (shared with the Customer and MO
 * modules). This class only guarantees the PEO-specific reporting tables
 * exist on first use.
 *
 * <p>Replaces the hardcoded {@code postgres:pass159357} single-host
 * connection from Assignment 1.
 */
public final class DatabaseConnection {

    private static final PostgresClusterConnection CLUSTER = new PostgresClusterConnection(new SecureLogger("peo-db"));
    private static volatile boolean schemaInitialized;

    private DatabaseConnection() {
    }

    /**
     * Opens a connection to the cluster.
     *
     * @return open JDBC connection
     * @throws SQLException when no cluster member is reachable
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = CLUSTER.getConnection();
        ensureReportingSchema(connection);
        return connection;
    }

    private static void ensureReportingSchema(Connection connection) throws SQLException {
        if (schemaInitialized) {
            return;
        }
        synchronized (DatabaseConnection.class) {
            if (schemaInitialized) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS payment_transactions (
                            transaction_id VARCHAR(64) PRIMARY KEY,
                            event_id INTEGER NOT NULL REFERENCES parking_events (event_id) ON DELETE RESTRICT,
                            vehicle_id INTEGER NOT NULL REFERENCES vehicles (vehicle_id) ON DELETE RESTRICT,
                            space_id INTEGER NOT NULL REFERENCES parking_spaces (space_id) ON DELETE RESTRICT,
                            zone_code VARCHAR(10) NOT NULL,
                            start_time TIMESTAMP NOT NULL,
                            stop_time TIMESTAMP NOT NULL,
                            amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS citations (
                            citation_id VARCHAR(64) PRIMARY KEY,
                            vehicle_id INTEGER NOT NULL REFERENCES vehicles (vehicle_id) ON DELETE RESTRICT,
                            space_id INTEGER NOT NULL REFERENCES parking_spaces (space_id) ON DELETE RESTRICT,
                            zone_code VARCHAR(10) NOT NULL,
                            inspection_time TIMESTAMP NOT NULL,
                            amount NUMERIC(10,2) NOT NULL CHECK (amount > 0),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_citations_vehicle_id ON citations (vehicle_id)");
            } catch (SQLException ignored) {
                schemaInitialized = true;
                return;
            }
            schemaInitialized = true;
        }
    }
}
