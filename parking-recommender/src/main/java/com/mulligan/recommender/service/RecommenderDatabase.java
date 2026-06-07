package com.mulligan.recommender.service;

import com.mulligan.common.cluster.PostgresClusterConnection;
import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;
import com.mulligan.recommender.model.RecommendationOption;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Database-backed recommendation service for one recommender node.
 */
public class RecommenderDatabase {

    private final PostgresClusterConnection cluster;
    private final SecureLogger log;

    /**
     * Creates a database service using the standard Mulligan DB environment
     * variables.
     */
    public RecommenderDatabase() {
        this.log = new SecureLogger("recommender-db");
        this.cluster = new PostgresClusterConnection(log);
    }

    /**
     * Computes this node's local recommendation list from the current database
     * snapshot.
     *
     * @param requestedSpace requested parking space number
     * @return recommended options according to Assignment 3
     */
    public List<RecommendationOption> recommendLocal(String requestedSpace) {
        validateSpace(requestedSpace);
        try (Connection conn = cluster.getConnection()) {
            Integer requestedZoneId = findRequestedZone(conn, requestedSpace);
            if (requestedZoneId == null) {
                throw new IllegalArgumentException("Invalid parking space.");
            }
            List<SpaceSnapshot> snapshots = loadZoneSnapshot(conn, requestedZoneId);
            return RecommendationAlgorithm.choose(requestedSpace, snapshots);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.security("Recommender DB error op=recommend err=" + e.getClass().getSimpleName());
            throw new RuntimeException("ERR_UNAVAILABLE");
        }
    }

    private void validateSpace(String requestedSpace) {
        try {
            InputValidator.requireSpace(requestedSpace);
        } catch (ValidationException e) {
            log.security("Recommender input rejected code=" + e.getMessage());
            throw new IllegalArgumentException("Invalid parking space.");
        }
    }

    private Integer findRequestedZone(Connection conn, String requestedSpace) throws Exception {
        String sql = """
                SELECT zone_id
                FROM parking_spaces
                WHERE space_number = ?
                  AND is_active = true
                """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, requestedSpace);
            try (var rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("zone_id") : null;
            }
        }
    }

    private List<SpaceSnapshot> loadZoneSnapshot(Connection conn, int zoneId) throws Exception {
        String sql = """
                SELECT ps.space_number,
                       COALESCE(c.citation_count, 0) AS citation_count,
                       CASE WHEN active.space_id IS NULL THEN true ELSE false END AS available
                FROM parking_spaces ps
                LEFT JOIN (
                    SELECT space_id, COUNT(*) AS citation_count
                    FROM citations
                    GROUP BY space_id
                ) c ON c.space_id = ps.space_id
                LEFT JOIN (
                    SELECT DISTINCT space_id
                    FROM parking_events
                    WHERE status = 'STARTED'
                      AND end_time IS NULL
                ) active ON active.space_id = ps.space_id
                WHERE ps.zone_id = ?
                  AND ps.is_active = true
                ORDER BY ps.space_number
                """;
        List<SpaceSnapshot> spaces = new ArrayList<>();
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, zoneId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    spaces.add(new SpaceSnapshot(
                            rs.getString("space_number"),
                            rs.getInt("citation_count"),
                            rs.getBoolean("available")
                    ));
                }
            }
        }
        return spaces;
    }
}
