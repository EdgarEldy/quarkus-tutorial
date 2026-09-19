package com.edgareldy.quarkustutorial.flyway;

import static org.junit.jupiter.api.Assertions.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Flyway V1 migration was applied to the Dev Services PostgreSQL and its constraints hold.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// The application migrates at start against the Dev Services PostgreSQL, so the injected DataSource
// already points at a fully migrated schema and no manual container or migration call is needed.
@QuarkusTest
class FlywaySchemaTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users", "roles", "permissions", "role_user", "role_permission", "activation_tokens",
            "blacklisted_tokens", "password_reset_tokens", "audit_logs", "categories", "products",
            "customers", "orders");

    @Inject
    DataSource dataSource;

    @Test
    void allExpectedTablesExistInPublicSchema() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertTrue(tables.containsAll(EXPECTED_TABLES), "Missing tables: " + missing(tables));
    }

    @Test
    void schemaHistoryShowsVersionOneSuccess() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT success FROM flyway_schema_history WHERE version = '1'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "No version 1 row in flyway_schema_history");
            assertTrue(rs.getBoolean(1));
        }
    }

    @Test
    void duplicateUserEmailIsRejectedByTheDatabase() throws SQLException {
        String sql = "INSERT INTO users (first_name, last_name, email, password) VALUES ('A', 'B', ?, 'x')";
        String email = "dup-" + System.nanoTime() + "@example.com";
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email);
                assertEquals(1, ps.executeUpdate());
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email);
                SQLException e = assertThrows(SQLException.class, ps::executeUpdate);
                assertEquals("23505", e.getSQLState());
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE email = ?")) {
                ps.setString(1, email);
                ps.executeUpdate();
            }
        }
    }

    private static Set<String> missing(Set<String> actual) {
        Set<String> m = new HashSet<>(EXPECTED_TABLES);
        m.removeAll(actual);
        return m;
    }
}
