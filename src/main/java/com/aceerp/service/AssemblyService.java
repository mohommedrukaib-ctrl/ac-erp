package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import com.aceerp.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AssemblyService {

    private static final Logger logger = LoggerFactory.getLogger(AssemblyService.class);
    private final InventoryService inventoryService = new InventoryService();
    private final AccountingService accountingService = new AccountingService();
    private final ProductRepository productRepo = new ProductRepository();

    public List<BillOfMaterials> getAllBoms() {
        List<BillOfMaterials> list = new ArrayList<>();
        String sql = """
            SELECT b.id, b.assembly_product_id, b.bom_code, b.description, b.total_cost,
                   p.product_code AS assy_code, p.name AS assy_name
            FROM bill_of_materials b
            JOIN products p ON b.assembly_product_id = p.id
            ORDER BY b.bom_code ASC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BillOfMaterials bom = new BillOfMaterials();
                bom.setId(rs.getInt("id"));
                bom.setAssemblyProductId(rs.getInt("assembly_product_id"));
                bom.setBomCode(rs.getString("bom_code"));
                bom.setDescription(rs.getString("description"));
                bom.setTotalCost(rs.getBigDecimal("total_cost"));
                bom.setAssemblyProductCode(rs.getString("assy_code"));
                bom.setAssemblyProductName(rs.getString("assy_name"));
                bom.setComponents(getComponentsForBom(conn, bom.getId()));
                list.add(bom);
            }
        } catch (SQLException e) {
            logger.error("Failed to list BOMs: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<BomComponent> getComponentsForBom(Connection conn, int bomId) throws SQLException {
        List<BomComponent> comps = new ArrayList<>();
        String sql = """
            SELECT c.id, c.bom_id, c.component_product_id, c.quantity, c.unit_cost,
                   p.product_code, p.name, p.unit
            FROM bom_components c
            JOIN products p ON c.component_product_id = p.id
            WHERE c.bom_id = ?
            ORDER BY c.id ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BomComponent comp = new BomComponent();
                    comp.setId(rs.getInt("id"));
                    comp.setBomId(rs.getInt("bom_id"));
                    comp.setComponentProductId(rs.getInt("component_product_id"));
                    comp.setQuantity(rs.getBigDecimal("quantity"));
                    comp.setUnitCost(rs.getBigDecimal("unit_cost"));
                    comp.setComponentProductCode(rs.getString("product_code"));
                    comp.setComponentProductName(rs.getString("name"));
                    comp.setUnit(rs.getString("unit"));
                    comps.add(comp);
                }
            }
        }
        return comps;
    }

    public BillOfMaterials saveBom(BillOfMaterials bom) {
        if (bom == null) return null;
        bom.recalculateTotalCost();

        if (bom.getBomCode() == null || bom.getBomCode().isBlank()) {
            bom.setBomCode(generateNextBomCode());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            if (bom.getId() <= 0) {
                String sql = """
                    INSERT INTO bill_of_materials (assembly_product_id, bom_code, description, total_cost)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, bom.getAssemblyProductId());
                    ps.setString(2, bom.getBomCode());
                    ps.setString(3, bom.getDescription());
                    ps.setBigDecimal(4, bom.getTotalCost());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) bom.setId(rs.getInt(1));
                    }
                }
            } else {
                String sql = """
                    UPDATE bill_of_materials
                    SET assembly_product_id = ?, description = ?, total_cost = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, bom.getAssemblyProductId());
                    ps.setString(2, bom.getDescription());
                    ps.setBigDecimal(3, bom.getTotalCost());
                    ps.setInt(4, bom.getId());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_components WHERE bom_id = ?")) {
                    ps.setInt(1, bom.getId());
                    ps.executeUpdate();
                }
            }

            // Insert Components
            String insertComp = """
                INSERT INTO bom_components (bom_id, component_product_id, quantity, unit_cost)
                VALUES (?, ?, ?, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertComp)) {
                for (BomComponent c : bom.getComponents()) {
                    ps.setInt(1, bom.getId());
                    ps.setInt(2, c.getComponentProductId());
                    ps.setBigDecimal(3, c.getQuantity());
                    ps.setBigDecimal(4, c.getUnitCost());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            
            // Update assembly product cost price in catalog
            productRepo.updateCostPrice(bom.getAssemblyProductId(), bom.getTotalCost());

            AuditLogService.log("SAVE_BOM", "BILL_OF_MATERIALS", bom.getId(), "Saved BOM " + bom.getBomCode());
            return bom;
        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to save BOM: {}", e.getMessage(), e);
            throw new RuntimeException("Save BOM failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    public AssemblyBuild buildAssembly(int bomId, int qtyToBuild, String notes, int userId) {
        if (qtyToBuild <= 0) throw new IllegalArgumentException("Quantity to build must be greater than zero.");

        BillOfMaterials bom = null;
        for (BillOfMaterials b : getAllBoms()) {
            if (b.getId() == bomId) { bom = b; break; }
        }
        if (bom == null) throw new IllegalArgumentException("BOM recipe not found.");

        // Validate raw material stock
        for (BomComponent comp : bom.getComponents()) {
            BigDecimal reqQty = comp.getQuantity().multiply(BigDecimal.valueOf(qtyToBuild));
            Product prod = productRepo.findById(comp.getComponentProductId()).orElse(null);
            if (prod == null || BigDecimal.valueOf(prod.getStockQuantity()).compareTo(reqQty) < 0) {
                throw new IllegalArgumentException(String.format(
                        "Insufficient stock for component %s. Required: %s, Available: %d",
                        comp.getComponentProductCode(), reqQty.toPlainString(), prod != null ? prod.getStockQuantity() : 0));
            }
        }

        BigDecimal buildTotalCost = bom.getTotalCost().multiply(BigDecimal.valueOf(qtyToBuild)).setScale(2, RoundingMode.HALF_UP);
        String buildNum = generateNextBuildNumber();

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Deduct component stocks
            for (BomComponent comp : bom.getComponents()) {
                int qtyToDeduct = comp.getQuantity().multiply(BigDecimal.valueOf(qtyToBuild)).setScale(0, RoundingMode.CEILING).intValue();
                inventoryService.stockOut(comp.getComponentProductId(), qtyToDeduct, "BUILD_ASSEMBLY", buildNum, "Used in build " + buildNum, userId);
            }

            // 2. Add finished assembly stock
            inventoryService.stockIn(bom.getAssemblyProductId(), qtyToBuild, "BUILD_ASSEMBLY", buildNum, "Built assembly " + buildNum, userId);

            // 3. Post Inventory GL Reclassification Entry (DR Finished Goods Inventory 1300 / CR Raw Materials Inventory 1300)
            Integer invAccId = accountingService.findAccountIdByCode(conn, "1300");
            Integer jeId = null;
            if (invAccId != null) {
                JournalEntry je = new JournalEntry();
                je.setEntryDate(LocalDate.now());
                je.setReferenceType("BUILD_ASSEMBLY");
                je.setDescription("Assembly Build " + buildNum + " (" + bom.getAssemblyProductCode() + " x" + qtyToBuild + ")");
                je.setAutoPosted(true);

                List<JournalEntryLine> lines = new ArrayList<>();
                JournalEntryLine drLine = new JournalEntryLine();
                drLine.setAccountId(invAccId);
                drLine.setDebit(buildTotalCost);
                drLine.setCredit(BigDecimal.ZERO);
                drLine.setDescription("Finished Unit Build " + buildNum);

                JournalEntryLine crLine = new JournalEntryLine();
                crLine.setAccountId(invAccId);
                crLine.setDebit(BigDecimal.ZERO);
                crLine.setCredit(buildTotalCost);
                crLine.setDescription("Component Usage " + buildNum);

                lines.add(drLine);
                lines.add(crLine);
                je.setLines(lines);

                JournalEntry postedJe = accountingService.postJournalEntry(je);
                if (postedJe != null) jeId = postedJe.getId();
            }

            // 4. Record Build Record
            AssemblyBuild build = new AssemblyBuild();
            build.setBuildNumber(buildNum);
            build.setBomId(bomId);
            build.setAssemblyProductId(bom.getAssemblyProductId());
            build.setQuantityToBuild(qtyToBuild);
            build.setTotalCost(buildTotalCost);
            build.setBuildDate(LocalDate.now());
            build.setJournalEntryId(jeId);
            build.setNotes(notes);

            String insertBuild = """
                INSERT INTO assembly_builds (build_number, bom_id, assembly_product_id, quantity_to_build, total_cost, build_date, journal_entry_id, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertBuild)) {
                ps.setString(1, build.getBuildNumber());
                ps.setInt(2, build.getBomId());
                ps.setInt(3, build.getAssemblyProductId());
                ps.setInt(4, build.getQuantityToBuild());
                ps.setBigDecimal(5, build.getTotalCost());
                ps.setDate(6, Date.valueOf(build.getBuildDate()));
                if (jeId != null) ps.setInt(7, jeId); else ps.setNull(7, Types.INTEGER);
                ps.setString(8, build.getNotes());
                if (userId > 0) ps.setInt(9, userId); else ps.setNull(9, Types.INTEGER);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) build.setId(rs.getInt(1));
                }
            }

            conn.commit();
            AuditLogService.log("BUILD_ASSEMBLY", "ASSEMBLY_BUILD", build.getId(), "Executed build " + buildNum);
            return build;

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Build assembly failed: {}", e.getMessage(), e);
            throw new RuntimeException("Build assembly failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    public List<AssemblyBuild> getAllBuilds() {
        List<AssemblyBuild> list = new ArrayList<>();
        String sql = """
            SELECT ab.id, ab.build_number, ab.bom_id, ab.assembly_product_id, ab.quantity_to_build,
                   ab.total_cost, ab.build_date, ab.notes,
                   b.bom_code, p.product_code, p.name
            FROM assembly_builds ab
            JOIN bill_of_materials b ON ab.bom_id = b.id
            JOIN products p ON ab.assembly_product_id = p.id
            ORDER BY ab.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AssemblyBuild ab = new AssemblyBuild();
                ab.setId(rs.getInt("id"));
                ab.setBuildNumber(rs.getString("build_number"));
                ab.setBomId(rs.getInt("bom_id"));
                ab.setAssemblyProductId(rs.getInt("assembly_product_id"));
                ab.setQuantityToBuild(rs.getInt("quantity_to_build"));
                ab.setTotalCost(rs.getBigDecimal("total_cost"));
                Date d = rs.getDate("build_date");
                if (d != null) ab.setBuildDate(d.toLocalDate());
                ab.setNotes(rs.getString("notes"));
                ab.setBomCode(rs.getString("bom_code"));
                ab.setAssemblyProductCode(rs.getString("product_code"));
                ab.setAssemblyProductName(rs.getString("name"));
                list.add(ab);
            }
        } catch (SQLException e) {
            logger.error("Failed to list builds: {}", e.getMessage(), e);
        }
        return list;
    }

    private String generateNextBomCode() {
        String sql = "SELECT bom_code FROM bill_of_materials ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("BOM-")) {
                    try {
                        int num = Integer.parseInt(last.substring(4)) + 1;
                        return String.format("BOM-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "BOM-00001";
    }

    private String generateNextBuildNumber() {
        String sql = "SELECT build_number FROM assembly_builds ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("BLD-")) {
                    try {
                        int num = Integer.parseInt(last.substring(4)) + 1;
                        return String.format("BLD-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "BLD-00001";
    }
}