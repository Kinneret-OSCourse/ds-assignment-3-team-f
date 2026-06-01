package com.mulligan.recommender;

import com.mulligan.common.cluster.PostgresClusterConnection;
import com.mulligan.common.logging.SecureLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads parking-space availability and citation counts from PostgreSQL.
 */
public class JdbcParkingSnapshotProvider implements ParkingSnapshotProvider {

    private final PostgresClusterConnection clusterConnection;

    /**
     * Creates a provider using the standard Mulligan database environment variables.
     */
    public JdbcParkingSnapshotProvider() {
        this.clusterConnection = new PostgresClusterConnection(new SecureLogger("recommender-db"));
    }

    /**
     * Loads all active and inactive spaces with their current citation counts.
     *
     * @return a snapshot list used by {@link RecommendationEngine}
     */
    @Override
    public List<ParkingSpaceSnapshot> loadSpaces() {
        String sql = """
                SELECT ps.space_number,
                       pz.zone_code,
                       COUNT(c.citation_id) AS citation_count,
                       ps.is_active
                         AND NOT EXISTS (
                             SELECT 1
                             FROM parking_events pe
                             WHERE pe.space_id = ps.space_id
                               AND pe.status = 'STARTED'
                               AND pe.end_time IS NULL
                         ) AS available
                FROM parking_spaces ps
                JOIN parking_zones pz ON pz.zone_id = ps.zone_id
                LEFT JOIN citations c ON c.space_id = ps.space_id
                GROUP BY ps.space_id, ps.space_number, pz.zone_code, ps.is_active
                ORDER BY pz.zone_code, ps.space_number
                """;

        try (var connection = clusterConnection.getConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            List<ParkingSpaceSnapshot> spaces = new ArrayList<>();
            while (resultSet.next()) {
                spaces.add(new ParkingSpaceSnapshot(
                        resultSet.getString("space_number"),
                        resultSet.getString("zone_code"),
                        resultSet.getInt("citation_count"),
                        resultSet.getBoolean("available")
                ));
            }
            return spaces;
        } catch (SQLException e) {
            throw new IllegalStateException("RECOMMENDER_UNAVAILABLE", e);
        }
    }
}
