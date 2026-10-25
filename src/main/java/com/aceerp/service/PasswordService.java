package com.aceerp.service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing and verification service using BCrypt.
 * 
 * HOW BCRYPT WORKS:
 * 
 * 1. HASHING (when creating/changing a password):
 *    - Input:  plain-text password
 *    - Process: BCrypt adds a random salt, then applies a slow hash function
 *    - Output: a 60-character string containing:
 *              - Algorithm version
 *              - Cost factor (how many iterations)
 *              - Salt
 *              - Hash
 *    - Example: $2a$12$abcdefghij...xyz123
 * 
 * 2. VERIFYING (during login):
 *    - Input:  plain-text password + stored hash
 *    - Process: Extract salt from hash, hash the input with same salt, compare
 *    - Output: true if they match, false otherwise
 * 
 * COST FACTOR:
 * The cost determines how slow the hash is (2^cost iterations).
 *   10 = ~100ms   (default, OK)
 *   12 = ~400ms   (recommended for modern apps)
 *   14 = ~1600ms  (very slow, maximum security)
 * 
 * We use 12: slow enough to defeat brute force, fast enough for user experience.
 */
public class PasswordService {

    // 2^12 = 4096 iterations. Takes ~400ms per hash. Good balance.
    private static final int BCRYPT_COST = 12;

    /**
     * Hash a plain-text password for storage.
     * 
     * @param plainPassword the user's chosen password
     * @return BCrypt hash (60 characters) safe to store in database
     * @throws IllegalArgumentException if password is null or empty
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        // gensalt() creates a random salt with the specified cost factor
        // hashpw() combines the password + salt and runs the hash function
        String salt = BCrypt.gensalt(BCRYPT_COST);
        return BCrypt.hashpw(plainPassword, salt);
    }

    /**
     * Verify that a plain-text password matches a stored hash.
     * 
     * @param plainPassword  the password the user typed (during login)
     * @param storedHash     the hash we have in the database
     * @return true if they match, false otherwise
     */
    public boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        try {
            // checkpw() handles salt extraction and comparison internally
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // The stored hash is malformed (e.g., corrupted in database)
            return false;
        }
    }

    /**
     * Check if a password meets minimum strength requirements.
     * Returns an error message, or null if the password is strong enough.
     * 
     * CURRENT RULES:
     *   - At least 8 characters
     *   - Contains at least one letter
     *   - Contains at least one digit
     * 
     * You can tighten these rules later (require symbols, uppercase, etc.)
     */
    public String validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c))  hasDigit = true;
        }

        if (!hasLetter) {
            return "Password must contain at least one letter.";
        }
        if (!hasDigit) {
            return "Password must contain at least one digit.";
        }

        return null; // Password is strong enough
    }
}