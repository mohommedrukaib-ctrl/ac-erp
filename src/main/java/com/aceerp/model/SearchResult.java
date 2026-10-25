package com.aceerp.model;

/**
 * Represents a single result from the universal search.
 * 
 * WHAT IS A RECORD?
 * Java "record" (introduced in Java 14) is a concise way to create a class
 * that simply holds data. Java automatically generates:
 *   - Constructor
 *   - Getter methods (category(), title(), subtitle(), etc.)
 *   - equals(), hashCode(), and toString() methods
 * 
 * It's perfect for data transfer objects (DTOs) — objects that carry data
 * between layers without any business logic.
 * 
 * USAGE EXAMPLE:
 *   SearchResult result = new SearchResult(
 *       "CUSTOMER", "Ahmed Ali", "Phone: 0501234567", "customer", 42
 *   );
 *   System.out.println(result.title()); // "Ahmed Ali"
 * 
 * @param category    The type: "CUSTOMER", "PRODUCT", "INVOICE", "SUPPLIER"
 * @param title       The main display text (customer name, product name, etc.)
 * @param subtitle    Additional info (phone number, product code, etc.)
 * @param moduleKey   Which module to navigate to when clicked
 * @param entityId    The database ID of the found item
 */
public record SearchResult(
    String category,
    String title,
    String subtitle,
    String moduleKey,
    int entityId
) {
    /**
     * Returns an icon character based on the category.
     * We use simple text symbols. In later phases, we can use proper icon fonts.
     */
    public String icon() {
        return switch (category) {
            case "CUSTOMER" -> "\uD83D\uDC64"; // 👤
            case "SUPPLIER" -> "\uD83C\uDFED"; // 🏭
            case "PRODUCT"  -> "\uD83D\uDCE6"; // 📦
            case "INVOICE"  -> "\uD83D\uDCC4"; // 📄
            default         -> "\uD83D\uDD0D"; // 🔍
        };
    }
}