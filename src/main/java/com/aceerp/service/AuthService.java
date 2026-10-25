package com.aceerp.service;

import com.aceerp.model.User;
import com.aceerp.model.UserSession;
import com.aceerp.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Authentication service.
 * 
 * Handles login and logout. Does NOT directly touch the database or passwords —
 * delegates to UserRepository and PasswordService.
 * 
 * THE RESULT PATTERN:
 * Login can fail in several ways:
 *   - User doesn't exist
 *   - Wrong password
 *   - Account is inactive
 * 
 * Instead of throwing exceptions (which should be reserved for programming errors),
 * we return a LoginResult that explicitly describes what happened.
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public AuthService() {
        this.userRepository = new UserRepository();
        this.passwordService = new PasswordService();
    }

    /**
     * Result of a login attempt. Encapsulates success/failure + user + error message.
     */
    public record LoginResult(boolean success, User user, String errorMessage) {

        public static LoginResult success(User user) {
            return new LoginResult(true, user, null);
        }

        public static LoginResult failure(String errorMessage) {
            return new LoginResult(false, null, errorMessage);
        }
    }

    /**
     * Attempt to log in with the given credentials.
     * 
     * SECURITY NOTE: We return the same error message for "user not found" and
     * "wrong password". This prevents attackers from discovering valid usernames.
     */
    public LoginResult login(String username, String password) {
        // Basic input validation
        if (username == null || username.trim().isEmpty()) {
            return LoginResult.failure("Please enter a username.");
        }
        if (password == null || password.isEmpty()) {
            return LoginResult.failure("Please enter a password.");
        }

        // Look up the user
        Optional<User> userOpt = userRepository.findByUsername(username.trim());

        if (userOpt.isEmpty()) {
            logger.warn("Login attempt for non-existent user: {}", username);
            return LoginResult.failure("Invalid username or password.");
        }

        User user = userOpt.get();

        // Check if account is active
        if (!user.isActive()) {
            logger.warn("Login attempt for inactive user: {}", username);
            return LoginResult.failure("This account has been deactivated. Contact your administrator.");
        }

        // Verify the password
        if (!passwordService.verifyPassword(password, user.getPasswordHash())) {
            logger.warn("Failed login (wrong password) for user: {}", username);
            return LoginResult.failure("Invalid username or password.");
        }

        // Success! Start the session
        userRepository.updateLastLogin(user.getId());
        UserSession.getInstance().login(user);

        return LoginResult.success(user);
    }

    /**
     * Log out the current user. Clears the session.
     */
    public void logout() {
        UserSession.getInstance().logout();
    }
}