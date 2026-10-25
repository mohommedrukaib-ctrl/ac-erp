package com.aceerp.model;

/**
 * User roles with associated permission levels.
 * 
 * ROLE HIERARCHY (higher level = more permissions):
 *   SUPER_ADMIN (100) - Full access, cannot be deleted, created during setup
 *   ADMIN       (80)  - Full business access, user management, settings
 *   MANAGER     (60)  - All business operations, reports
 *   USER        (40)  - Standard user: sales, customers, basic ops
 *   TECHNICIAN  (20)  - Service job cards only
 * 
 * WHY ENUM?
 * An enum is a special Java type with a fixed set of values. Benefits:
 *   - Type-safe: can't accidentally use "admn" or "Admin"
 *   - Easy to iterate (for dropdowns)
 *   - Can hold data (like displayName and level)
 */
public enum UserRole {

    SUPER_ADMIN("Super Administrator", 100),
    ADMIN      ("Administrator",       80),
    MANAGER    ("Manager",             60),
    USER       ("User",                40),
    TECHNICIAN ("Technician",          20);

    private final String displayName;
    private final int level;

    UserRole(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Check if this role has at least the required permission level.
     * Example: SUPER_ADMIN.hasAtLeast(ADMIN) → true
     *          USER.hasAtLeast(ADMIN) → false
     */
    public boolean hasAtLeast(UserRole required) {
        return this.level >= required.level;
    }

    /**
     * Safe conversion from String (used when loading from database).
     * Returns null if the string doesn't match any role.
     */
    public static UserRole fromString(String value) {
        if (value == null) return null;
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}