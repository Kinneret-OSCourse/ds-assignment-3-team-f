package com.mulligan.service;

import com.mulligan.common.cluster.PostgresClusterConnection;
import com.mulligan.common.logging.SecureLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides JDBC connections for the Customer UI module against the 3-node
 * Patroni PostgreSQL cluster.
 *
 * <p>The actual cluster-aware URL construction lives in
 * {@link PostgresClusterConnection} (shared with the PEO and MO modules). This
 * class just ensures the reporting tables exist on first connection.
 *
 * <p>Replaces the hardcoded {@code postgres:pass159357} single-host connection
 * from Assignment 1.
 */
public final class DatabaseConnection {

    private static final PostgresClusterConnection CLUSTER = new PostgresClusterConnection(new SecureLogger("customer-db"));
    private static volatile boolean schemaInitialized;

    private DatabaseConnection() {
    }

    /**
     * Opens a new connection to the primary Postgres node. Throws
     * {@link SQLException} if the cluster cannot accept the connection - the
     * caller must translate this into a generic
     * {@code ClientErrorCodes.UNAVAILABLE} response so internal driver
     * messages never leak.
     *
     * @return an open JDBC connection
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
                statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_transactions_vehicle_id ON payment_transactions (vehicle_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_transactions_stop_time ON payment_transactions (stop_time DESC)");
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
                statement.execute("CREATE INDEX IF NOT EXISTS idx_citations_inspection_time ON citations (inspection_time DESC)");
                statement.execute("ALTER TABLE parking_events ADD COLUMN IF NOT EXISTS customer_id VARCHAR(32)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_events_customer_active ON parking_events (customer_id, status) WHERE end_time IS NULL");
            } catch (SQLException sqle) {
                // Do not fail the connection if migrations cannot run (e.g. on a
                // read-replica). Mark initialised so we do not retry every call.
                schemaInitialized = true;
                return;
            }
            schemaInitialized = true;
        }
    }
}
