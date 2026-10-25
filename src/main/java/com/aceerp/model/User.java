package com.aceerp.model;

import java.time.LocalDateTime;

/**
 * User entity representing an application user.
 * 
 * WHY A CLASS INSTEAD OF A RECORD?
 * Users are MUTABLE (we edit them: change role, deactivate, update name).
 * Records are IMMUTABLE (good for data transfer, not for editable entities).
 * 
 * NOTE: We never store the plain password. passwordHash is a BCrypt hash.
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
    private boolean active;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default constructor (needed by some frameworks / for building objects)
    public User() {}

    // Convenience constructor for creating new users
    public User(String username, String passwordHash, String fullName, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.active = true;
    }

    // ===== Getters and Setters =====

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Convenience method for displaying the role in TableView columns.
     */
    public String getRoleDisplayName() {
        return role != null ? role.getDisplayName() : "";
    }

    /**
     * Convenience method for displaying active status in TableView columns.
     */
    public String getStatusDisplay() {
        return active ? "Active" : "Inactive";
    }
}