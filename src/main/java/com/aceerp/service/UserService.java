package com.aceerp.service;

import com.aceerp.model.User;
import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import com.aceerp.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for user management.
 * 
 * Enforces:
 *   - Permission rules (who can create/modify users)
 *   - Validation (username format, password strength)
 *   - Business rules (don't allow deactivating the only super admin)
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public UserService() {
        this.userRepository = new UserRepository();
        this.passwordService = new PasswordService();
    }

    /**
     * Check if the setup wizard needs to run.
     * True when there are no users in the database (fresh install).
     */
    public boolean needsInitialSetup() {
        return userRepository.countAll() == 0;
    }

    /**
     * Create the first SUPER_ADMIN account (setup wizard only).
     * This method does NOT check permissions because no user is logged in yet.
     * It verifies no users exist as a safety check.
     * 
     * @throws IllegalStateException if users already exist
     * @throws IllegalArgumentException if validation fails
     */
    public User createSuperAdmin(String username, String password, String fullName)
            throws SQLException {

        // Safety check: this method should only run during initial setup
        if (userRepository.countAll() > 0) {
            throw new IllegalStateException(
                "Cannot create super admin: users already exist. Use createUser() instead.");
        }

        validateUsername(username);
        validateFullName(fullName);

        String passwordError = passwordService.validatePasswordStrength(password);
                if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        String hash = passwordService.hashPassword(password);
        User user = new User(username.trim(), hash, fullName.trim(), UserRole.SUPER_ADMIN);
        user.setActive(true);

        // createdBy is null for the first user
        userRepository.insert(user, null);

        logger.info("SUPER_ADMIN account created during setup: {}", username);
        return user;
    }

    /**
     * Create a new user. Requires ADMIN or SUPER_ADMIN permission.
     */
    public User createUser(String username, String password, String fullName,
                           String email, String phone, UserRole role) throws SQLException {

        requirePermission(UserRole.ADMIN);

        validateUsername(username);
        validateFullName(fullName);
        if (role == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        // Only SUPER_ADMIN can create another SUPER_ADMIN
        if (role == UserRole.SUPER_ADMIN &&
            UserSession.getInstance().getCurrentUser().getRole() != UserRole.SUPER_ADMIN) {
            throw new SecurityException("Only a Super Administrator can create another Super Administrator.");
        }

        if (userRepository.usernameExists(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        String passwordError = passwordService.validatePasswordStrength(password);
        if (passwordError != null) {
            throw new IllegalArgumentException(passwordError);
        }

        String hash = passwordService.hashPassword(password);
        User user = new User(username.trim(), hash, fullName.trim(), role);
        user.setEmail(email == null || email.trim().isEmpty() ? null : email.trim());
        user.setPhone(phone == null || phone.trim().isEmpty() ? null : phone.trim());
        user.setActive(true);

        int currentUserId = UserSession.getInstance().getCurrentUser().getId();
        userRepository.insert(user, currentUserId);

        return user;
    }

    /**
     * Update an existing user's details (not password).
     */
    public void updateUser(User user) throws SQLException {
        requirePermission(UserRole.ADMIN);

        validateFullName(user.getFullName());
        if (user.getRole() == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        User currentUser = UserSession.getInstance().getCurrentUser();

        // Prevent demoting the last super admin
        Optional<User> existingOpt = userRepository.findById(user.getId());
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found.");
        }
        User existing = existingOpt.get();

        if (existing.getRole() == UserRole.SUPER_ADMIN && user.getRole() != UserRole.SUPER_ADMIN) {
            long superAdminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN && u.isActive())
                .count();
            if (superAdminCount <= 1) {
                throw new IllegalStateException(
                    "Cannot demote the last active Super Administrator.");
            }
        }

        // Only SUPER_ADMIN can modify another SUPER_ADMIN or promote to SUPER_ADMIN
        if ((existing.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.SUPER_ADMIN)
            && currentUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new SecurityException(
                "Only a Super Administrator can modify Super Administrator accounts.");
        }

        userRepository.update(user, currentUser.getId());
    }

    /**
     * Deactivate a user. Prevents self-deactivation and deactivating the last super admin.
     */
    public void deactivateUser(int userId) throws SQLException {
        requirePermission(UserRole.ADMIN);

        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser.getId() == userId) {
            throw new IllegalStateException("You cannot deactivate your own account.");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found.");
        }

        User user = userOpt.get();

        if (user.getRole() == UserRole.SUPER_ADMIN) {
            long activeSuperAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN && u.isActive())
                .count();
            if (activeSuperAdmins <= 1) {
                throw new IllegalStateException(
                    "Cannot deactivate the last active Super Administrator.");
            }
        }

        user.setActive(false);
        userRepository.update(user, currentUser.getId());
    }

    /**
     * Change a user's password. 
     * - ADMIN+ can change any password without knowing the old one.
     * - A user can change their own password if they provide the old one.
     * (For simplicity in this phase, we only allow admins to change passwords.)
     */
    public void changePassword(int userId, String newPassword) throws SQLException {
        requirePermission(UserRole.ADMIN);

        String err = passwordService.validatePasswordStrength(newPassword);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }

        String hash = passwordService.hashPassword(newPassword);
        int currentUserId = UserSession.getInstance().getCurrentUser().getId();
        userRepository.updatePassword(userId, hash, currentUserId);
    }

    /**
     * Get all users (for user management list).
     */
    public List<User> listAllUsers() {
        requirePermission(UserRole.ADMIN);
        return userRepository.findAll();
    }

    // ===== Validation helpers =====

    private void validateUsername(String username) {
        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("Username cannot exceed 50 characters.");
        }
        // Allow letters, digits, dots, underscores, hyphens
        if (!username.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException(
                "Username can only contain letters, digits, dots, underscores, and hyphens.");
        }
    }

    private void validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (fullName.length() > 100) {
            throw new IllegalArgumentException("Full name cannot exceed 100 characters.");
        }
    }

    /**
     * Enforce that the currently logged-in user has at least the required role.
     */
    private void requirePermission(UserRole required) {
        UserSession session = UserSession.getInstance();
        if (!session.isLoggedIn()) {
            throw new SecurityException("You must be logged in.");
        }
        if (!session.hasRole(required)) {
            throw new SecurityException(
                "You do not have permission for this operation. Required role: " + required);
        }
    }
}