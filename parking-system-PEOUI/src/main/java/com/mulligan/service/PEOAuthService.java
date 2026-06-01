package com.mulligan.service;

import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.sql.SQLException;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Validates parking enforcement officer credentials against the database.
 */
public class PEOAuthService {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    public boolean authenticate(String userId, String password) {
        String sql = """
                SELECT password_hash, password_salt, password_iterations
                FROM peo_users
                WHERE user_id = ?
                  AND is_active = true
                """;

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);

            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                byte[] expectedHash = HexFormat.of().parseHex(rs.getString("password_hash"));
                byte[] salt = HexFormat.of().parseHex(rs.getString("password_salt"));
                int iterations = rs.getInt("password_iterations");
                byte[] actualHash = hashPassword(password, salt, iterations, expectedHash.length);

                return MessageDigest.isEqual(expectedHash, actualHash);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Login failed because the PEO user database is unavailable.", e);
        } catch (Exception e) {
            throw new RuntimeException("Login failed because PEO credentials could not be verified.", e);
        }
    }

    private byte[] hashPassword(String password, byte[] salt, int iterations, int keyLengthBytes) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBytes * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
    }
}
