package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.User;
import com.aceerp.model.UserRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity CRUD (Create, Read, Update, Delete) operations.
 * 
 * REPOSITORY PATTERN:
 * This class is the ONLY place in the application that writes SQL for the users table.
 * If we ever change database vendors or table structure, we change this one file.
 * The service layer calls methods like findByUsername(), it doesn't know SQL.
 */
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    /**
     * Count the total number of users in the database.
     * Used by the setup wizard to decide if initial setup is needed.
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count users: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Find a user by their username.
     * Returns Optional.empty() if no user with that username exists.
     * 
     * WHY OPTIONAL?
     * Optional is a Java class that explicitly represents "maybe a value, maybe not".
     * Forces calling code to handle the "not found" case, preventing NullPointerException.
     * 
     * USAGE:
     *   Optional<User> result = userRepository.findByUsername("admin");
     *   if (result.isPresent()) {
     *       User user = result.get();
     *       // use user
     *   }
     */
    public Optional<User> findByUsername(String username) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role,
                   is_active, last_login, created_at, updated_at
            FROM users
            WHERE LOWER(username) = LOWER(?)
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find user by username '{}': {}", username, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Find a user by their ID.
     */
    public Optional<User> findById(int id) {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role,
                   is_active, last_login, created_at, updated_at
            FROM users
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find user by id {}: {}", id, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Return all users ordered by username.
     */
    public List<User> findAll() {
        String sql = """
            SELECT id, username, password_hash, full_name, email, phone, role,
                   is_active, last_login, created_at, updated_at
            FROM users
            ORDER BY username
            """;

        List<User> users = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to list users: {}", e.getMessage());
        }

        return users;
    }

    /**
     * Insert a new user. Returns the generated ID.
     * 
     * RETURN_GENERATED_KEYS: PostgreSQL will tell us the auto-generated ID 
     * after INSERT (because the id column is SERIAL).
     */
    public int insert(User user, Integer createdByUserId) throws SQLException {
        String sql = """
            INSERT INTO users 
                (username, password_hash, full_name, email, phone, role, is_active, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getEmail());
            stmt.setString(5, user.getPhone());
            stmt.setString(6, user.getRole().name());
            stmt.setBoolean(7, user.isActive());

            // Audit columns: can be null for the very first user (setup wizard)
            if (createdByUserId != null) {
                stmt.setInt(8, createdByUserId);
                stmt.setInt(9, createdByUserId);
            } else {
                stmt.setNull(8, java.sql.Types.INTEGER);
                stmt.setNull(9, java.sql.Types.INTEGER);
            }

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int newId = keys.getInt(1);
                    user.setId(newId);
                    logger.info("Created user: {} (id={})", user.getUsername(), newId);
                    return newId;
                }
            }

            throw new SQLException("User inserted but no ID returned.");
        }
    }

    /**
     * Update an existing user's details.
     * Password is updated separately via updatePassword().
     */
    public void update(User user, int updatedByUserId) throws SQLException {
        String sql = """
            UPDATE users
            SET full_name = ?, email = ?, phone = ?, role = ?, is_active = ?,
                updated_at = CURRENT_TIMESTAMP, updated_by = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPhone());
            stmt.setString(4, user.getRole().name());
            stmt.setBoolean(5, user.isActive());
            stmt.setInt(6, updatedByUserId);
            stmt.setInt(7, user.getId());

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("User not found: id=" + user.getId());
            }
            logger.info("Updated user: {} (id={})", user.getUsername(), user.getId());
        }
    }

    /**
     * Update only the password hash. Separate method so audit logs make sense.
     */
    public void updatePassword(int userId, String newPasswordHash, int updatedByUserId)
            throws SQLException {

        String sql = """
            UPDATE users 
            SET password_hash = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, updatedByUserId);
            stmt.setInt(3, userId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("User not found: id=" + userId);
            }
            logger.info("Password updated for user id={}", userId);
        }
    }

    /**
     * Update the last_login timestamp. Called after successful login.
     */
    public void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Non-critical: log but don't fail the login
            logger.warn("Failed to update last_login for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Check if a username is already taken (case-insensitive).
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Failed to check username existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Convert a database row (ResultSet) into a User object.
     * This is called whenever we SELECT from the users table.
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setRole(UserRole.fromString(rs.getString("role")));
        user.setActive(rs.getBoolean("is_active"));

        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) user.setLastLogin(lastLogin.toLocalDateTime());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) user.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) user.setUpdatedAt(updatedAt.toLocalDateTime());

        return user;
    }
}