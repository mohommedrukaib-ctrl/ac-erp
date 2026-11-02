package com.aceerp.service;


import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.FixedAsset;
import com.aceerp.model.JournalEntry;
import com.aceerp.model.JournalEntryLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FixedAssetService {

    private static final Logger logger = LoggerFactory.getLogger(FixedAssetService.class);
    private final AccountingService accountingService = new AccountingService();

    public List<FixedAsset> getAllAssets() {
        List<FixedAsset> list = new ArrayList<>();
        String sql = "SELECT * FROM fixed_assets ORDER BY asset_code ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAsset(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to list fixed assets: {}", e.getMessage(), e);
        }
        return list;
    }

    public FixedAsset saveAsset(FixedAsset asset) {
        if (asset == null) return null;
        asset.recalculateBookValue();

        if (asset.getAssetCode() == null || asset.getAssetCode().isBlank()) {
            asset.setAssetCode(generateNextAssetCode());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();

            // Default account lookup if missing
            if (asset.getAssetAccountId() == null || asset.getAssetAccountId() <= 0) {
                asset.setAssetAccountId(accountingService.findAccountIdByCode(conn, "1500"));
            }
            if (asset.getAccumulatedDepAccountId() == null || asset.getAccumulatedDepAccountId() <= 0) {
                asset.setAccumulatedDepAccountId(accountingService.findAccountIdByCode(conn, "1590"));
            }
            if (asset.getDepExpenseAccountId() == null || asset.getDepExpenseAccountId() <= 0) {
                asset.getDepExpenseAccountId();
                asset.setDepExpenseAccountId(accountingService.findAccountIdByCode(conn, "6800"));
            }

            if (asset.getId() <= 0) {
                String sql = """
                    INSERT INTO fixed_assets 
                        (asset_code, asset_name, category, acquisition_date, acquisition_cost, salvage_value,
                         useful_life_years, depreciation_method, accumulated_depreciation, book_value,
                         asset_account_id, accumulated_dep_account_id, dep_expense_account_id, status, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, asset.getAssetCode());
                    ps.setString(2, asset.getAssetName());
                    ps.setString(3, asset.getCategory());
                    ps.setDate(4, Date.valueOf(asset.getAcquisitionDate()));
                    ps.setBigDecimal(5, asset.getAcquisitionCost());
                    ps.setBigDecimal(6, asset.getSalvageValue());
                    ps.setInt(7, asset.getUsefulLifeYears());
                    ps.setString(8, asset.getDepreciationMethod());
                    ps.setBigDecimal(9, asset.getAccumulatedDepreciation());
                    ps.setBigDecimal(10, asset.getBookValue());
                    setNullableInt(ps, 11, asset.getAssetAccountId());
                    setNullableInt(ps, 12, asset.getAccumulatedDepAccountId());
                    setNullableInt(ps, 13, asset.getDepExpenseAccountId());
                    ps.setString(14, asset.getStatus());
                    ps.setString(15, asset.getNotes());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) asset.setId(rs.getInt(1));
                    }
                }
            } else {
                String sql = """
                    UPDATE fixed_assets
                    SET asset_name = ?, category = ?, acquisition_date = ?, acquisition_cost = ?,
                        salvage_value = ?, useful_life_years = ?, depreciation_method = ?,
                        accumulated_depreciation = ?, book_value = ?, asset_account_id = ?,
                        accumulated_dep_account_id = ?, dep_expense_account_id = ?, status = ?, notes = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, asset.getAssetName());
                    ps.setString(2, asset.getCategory());
                    ps.setDate(3, Date.valueOf(asset.getAcquisitionDate()));
                    ps.setBigDecimal(4, asset.getAcquisitionCost());
                    ps.setBigDecimal(5, asset.getSalvageValue());
                    ps.setInt(6, asset.getUsefulLifeYears());
                    ps.setString(7, asset.getDepreciationMethod());
                    ps.setBigDecimal(8, asset.getAccumulatedDepreciation());
                    ps.setBigDecimal(9, asset.getBookValue());
                    setNullableInt(ps, 10, asset.getAssetAccountId());
                    setNullableInt(ps, 11, asset.getAccumulatedDepAccountId());
                    setNullableInt(ps, 12, asset.getDepExpenseAccountId());
                    ps.setString(13, asset.getStatus());
                    ps.setString(14, asset.getNotes());
                    ps.setInt(15, asset.getId());
                    ps.executeUpdate();
                }
            }

            AuditLogService.log("SAVE_FIXED_ASSET", "FIXED_ASSET", asset.getId(), "Saved asset " + asset.getAssetCode());
            return asset;
        } catch (SQLException e) {
            logger.error("Failed to save fixed asset: {}", e.getMessage(), e);
            throw new RuntimeException("Save asset failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public int postMonthlyDepreciationRun(LocalDate date) {
        List<FixedAsset> activeAssets = getAllAssets().stream()
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getBookValue().compareTo(a.getSalvageValue()) > 0)
                .toList();

        if (activeAssets.isEmpty()) return 0;

        int postedCount = 0;
        for (FixedAsset asset : activeAssets) {
            BigDecimal monthlyDep = asset.calculateMonthlyDepreciation();
            if (monthlyDep.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Check if already depreciated for this year/month
            if (isDepreciatedForPeriod(asset.getId(), date.getYear(), date.getMonthValue())) {
                continue;
            }

            try {
                // Post GL Entry: DR 6800 Dep Expense | CR 1590 Accum Dep
                JournalEntry je = new JournalEntry();
                je.setEntryDate(date);
                je.setReferenceType("FIXED_ASSET_DEP");
                je.setReferenceId(asset.getId());
                je.setDescription("Monthly Depreciation: " + asset.getAssetCode() + " - " + asset.getAssetName());
                je.setAutoPosted(true);

                Integer expAccId = asset.getDepExpenseAccountId();
                Integer accumAccId = asset.getAccumulatedDepAccountId();

                if (expAccId == null || expAccId <= 0 || accumAccId == null || accumAccId <= 0) {
                    try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                        expAccId = accountingService.findAccountIdByCode(conn, "6800");
                        accumAccId = accountingService.findAccountIdByCode(conn, "1590");
                    }
                }

                if (expAccId == null || accumAccId == null) {
                    logger.warn("Depreciation GL accounts missing for asset {}", asset.getAssetCode());
                    continue;
                }

                List<JournalEntryLine> lines = new ArrayList<>();
                JournalEntryLine drLine = new JournalEntryLine();
                drLine.setAccountId(expAccId);
                drLine.setDebit(monthlyDep);
                drLine.setCredit(BigDecimal.ZERO);
                drLine.setDescription("Depreciation Expense " + asset.getAssetCode());

                JournalEntryLine crLine = new JournalEntryLine();
                crLine.setAccountId(accumAccId);
                crLine.setDebit(BigDecimal.ZERO);
                crLine.setCredit(monthlyDep);
                crLine.setDescription("Accumulated Depreciation " + asset.getAssetCode());

                lines.add(drLine);
                lines.add(crLine);
                je.setLines(lines);

                JournalEntry postedJe = accountingService.postJournalEntry(je);

                // Update asset accumulated dep + book value
                asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(monthlyDep));
                asset.recalculateBookValue();
                if (asset.getBookValue().compareTo(asset.getSalvageValue()) <= 0) {
                    asset.setStatus("FULLY_DEPRECIATED");
                }
                saveAsset(asset);

                // Record history
                recordDepreciationHistory(asset.getId(), date, date.getYear(), date.getMonthValue(), monthlyDep, postedJe.getId());
                postedCount++;

            } catch (Exception e) {
                logger.error("Failed to post depreciation for asset {}: {}", asset.getAssetCode(), e.getMessage(), e);
            }
        }
        return postedCount;
    }

    private boolean isDepreciatedForPeriod(int assetId, int year, int month) {
        String sql = "SELECT COUNT(*) FROM fixed_asset_depreciations WHERE fixed_asset_id = ? AND period_year = ? AND period_month = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assetId);
            ps.setInt(2, year);
            ps.setInt(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Failed period check: {}", e.getMessage(), e);
        }
        return false;
    }

    private void recordDepreciationHistory(int assetId, LocalDate date, int year, int month, BigDecimal amt, int jeId) throws SQLException {
        String sql = """
            INSERT INTO fixed_asset_depreciations (fixed_asset_id, depreciation_date, period_year, period_month, depreciation_amount, journal_entry_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assetId);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, year);
            ps.setInt(4, month);
            ps.setBigDecimal(5, amt);
            ps.setInt(6, jeId);
            ps.executeUpdate();
        }
    }

    private String generateNextAssetCode() {
        String sql = "SELECT asset_code FROM fixed_assets ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("FA-")) {
                    try {
                        int num = Integer.parseInt(last.substring(3)) + 1;
                        return String.format("FA-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "FA-00001";
    }

    private FixedAsset mapAsset(ResultSet rs) throws SQLException {
        FixedAsset fa = new FixedAsset();
        fa.setId(rs.getInt("id"));
        fa.setAssetCode(rs.getString("asset_code"));
        fa.setAssetName(rs.getString("asset_name"));
        fa.setCategory(rs.getString("category"));
        Date d = rs.getDate("acquisition_date");
        if (d != null) fa.setAcquisitionDate(d.toLocalDate());
        fa.setAcquisitionCost(rs.getBigDecimal("acquisition_cost"));
        fa.setSalvageValue(rs.getBigDecimal("salvage_value"));
        fa.setUsefulLifeYears(rs.getInt("useful_life_years"));
        fa.setDepreciationMethod(rs.getString("depreciation_method"));
        fa.setAccumulatedDepreciation(rs.getBigDecimal("accumulated_depreciation"));
        fa.setBookValue(rs.getBigDecimal("book_value"));
        fa.setAssetAccountId(getNullableInt(rs, "asset_account_id"));
        fa.setAccumulatedDepAccountId(getNullableInt(rs, "accumulated_dep_account_id"));
        fa.setDepExpenseAccountId(getNullableInt(rs, "dep_expense_account_id"));
        fa.setStatus(rs.getString("status"));
        fa.setNotes(rs.getString("notes"));
        return fa;
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null && val > 0) ps.setInt(idx, val);
        else ps.setNull(idx, Types.INTEGER);
    }

    private Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}