package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Product;
import com.aceerp.model.StockMovement;
import com.aceerp.repository.ProductRepository;
import com.aceerp.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for inventory operations.
 * 
 * Key rule: Every stock change MUST be recorded as a movement.
 * The product's stock_quantity and the movement record are updated
 * together in a single transaction — either both succeed or both fail.
 */
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final StockMovementRepository movementRepo = new StockMovementRepository();
    private final ProductRepository productRepo = new ProductRepository();

    /**
     * Records incoming stock (purchases, returns, etc.).
     * 
     * @param productId  which product
     * @param qty        how many units coming in (must be positive)
     * @param refType    reference type like "PURCHASE" or "RETURN"
     * @param refNum     reference number like "PO-001"
     * @param notes      optional notes
     * @param userId     who is doing this
     */
    public void stockIn(int productId, int qty, String refType,
                        String refNum, String notes, int userId) {
        // Validate
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        // Use a transaction: movement + stock update happen together
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get current stock
                int currentStock = getCurrentStock(productId, conn);
                int newStock = currentStock + qty;

                // Create movement record
                StockMovement m = new StockMovement();
                m.setProductId(productId);
                m.setMovementType("IN");
                m.setQuantity(qty);
                m.setPreviousStock(currentStock);
                m.setNewStock(newStock);
                m.setReferenceType(refType);
                m.setReferenceNumber(refNum);
                m.setNotes(notes);
                m.setCreatedBy(userId);

                movementRepo.recordMovement(m, conn);
                productRepo.updateStock(productId, newStock, conn);

                conn.commit();
                log.info("Stock IN: product {}, qty +{}, new stock {}",
                    productId, qty, newStock);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Stock IN failed for product " + productId, e);
            throw new RuntimeException("Failed to record stock in: " + e.getMessage(), e);
        }
    }

    /**
     * Records outgoing stock (sales, jobs, etc.).
     */
    public void stockOut(int productId, int qty, String refType,
                         String refNum, String notes, int userId) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int currentStock = getCurrentStock(productId, conn);

                // Check if enough stock is available
                if (qty > currentStock) {
                    throw new IllegalArgumentException(
                        "Insufficient stock. Available: " + currentStock +
                        ", Requested: " + qty);
                }

                int newStock = currentStock - qty;

                StockMovement m = new StockMovement();
                m.setProductId(productId);
                m.setMovementType("OUT");
                m.setQuantity(qty);
                m.setPreviousStock(currentStock);
                m.setNewStock(newStock);
                m.setReferenceType(refType);
                m.setReferenceNumber(refNum);
                m.setNotes(notes);
                m.setCreatedBy(userId);

                movementRepo.recordMovement(m, conn);
                productRepo.updateStock(productId, newStock, conn);

                conn.commit();
                log.info("Stock OUT: product {}, qty -{}, new stock {}",
                    productId, qty, newStock);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Stock OUT failed for product " + productId, e);
            throw new RuntimeException("Failed to record stock out: " + e.getMessage(), e);
        }
    }

    /**
     * Adjusts stock to a specific count (e.g., after physical counting).
     * The user enters the ACTUAL count, and we calculate the difference.
     */
    public void adjustStock(int productId, int actualCount, String reason, int userId) {
        if (actualCount < 0) {
            throw new IllegalArgumentException("Stock count cannot be negative");
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int currentStock = getCurrentStock(productId, conn);
                int diff = actualCount - currentStock;

                if (diff == 0) {
                    throw new IllegalArgumentException(
                        "No adjustment needed. Stock is already " + currentStock);
                }

                StockMovement m = new StockMovement();
                m.setProductId(productId);
                m.setMovementType("ADJUSTMENT");
                m.setQuantity(Math.abs(diff));
                m.setPreviousStock(currentStock);
                m.setNewStock(actualCount);
                m.setReferenceType("ADJUSTMENT");
                m.setReferenceNumber(null);
                m.setNotes(reason);
                m.setCreatedBy(userId);

                movementRepo.recordMovement(m, conn);
                productRepo.updateStock(productId, actualCount, conn);

                conn.commit();
                log.info("Stock ADJUST: product {}, {} -> {}, reason: {}",
                    productId, currentStock, actualCount, reason);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Stock ADJUST failed for product " + productId, e);
            throw new RuntimeException("Failed to adjust stock: " + e.getMessage(), e);
        }
    }

    /** Gets recent movements, optionally filtered by type */
    public List<StockMovement> getRecentMovements(String filterType, int limit) {
        return movementRepo.getRecentMovements(filterType, limit);
    }

    /** Gets all movements for a specific product */
    public List<StockMovement> getMovementsByProduct(int productId) {
        return movementRepo.getMovementsByProduct(productId);
    }

    /** Gets products where stock is at or below the minimum level */
    public List<Product> getLowStockProducts() {
        return productRepo.getLowStock();
    }

    /** Searches products for the smart picker */
    public List<Product> searchProducts(String query) {
        return productRepo.search(query);
    }

    /**
     * Reads the current stock for a product using the same transaction connection.
     */
    private int getCurrentStock(int productId, Connection conn) throws SQLException {
        String sql = "SELECT stock_quantity FROM products WHERE id = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_quantity");
                }
            }
        }
        throw new IllegalArgumentException("Product not found: ID " + productId);
    }

        
}