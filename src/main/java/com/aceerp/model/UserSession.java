package com.aceerp.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Represents the currently logged-in user's session.
 * 
 * SINGLETON PATTERN:
 * There is only ONE active session at a time (desktop app, one user per window).
 * All parts of the app check UserSession.getInstance().getCurrentUser() to know
 * who is logged in.
 * 
 * WHAT GOES IN SESSION:
 * - The User object (so we have their name, role, id)
 * - Login timestamp
 * - Later: permissions cache, preferences
 * 
 * WHAT DOESN'T GO IN SESSION:
 * - The password or hash (we never need it after login)
 */
public final class UserSession {

    private static final Logger logger = LoggerFactory.getLogger(UserSession.class);

    private static UserSession instance;

    private User currentUser;
    private LocalDateTime loginTime;

    private UserSession() {}

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    /**
     * Start a session for the given user. Called after successful login.
     */
    public void login(User user) {
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
        logger.info("User logged in: {} ({})", user.getUsername(), user.getRole());
    }

    /**
     * End the current session. Called on logout.
     */
    public void logout() {
        if (currentUser != null) {
            logger.info("User logged out: {}", currentUser.getUsername());
        }
        this.currentUser = null;
        this.loginTime = null;
    }

    /**
     * Returns the currently logged-in user, or null if no one is logged in.
     * ALWAYS check for null before using.
     */
    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    /**
     * Check if the current user has at least the required role.
     * Returns false if nobody is logged in.
     */
    public boolean hasRole(UserRole requiredRole) {
        if (currentUser == null) return false;
        return currentUser.getRole().hasAtLeast(requiredRole);
    }
}   