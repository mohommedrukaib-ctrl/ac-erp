package com.aceerp.util;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.sql.Statement;

public final class GlobalQuickAdd {

    private static final Logger log = LoggerFactory.getLogger(GlobalQuickAdd.class);

    private GlobalQuickAdd() {}

    public static void showCustomerDialog(Runnable onSuccess) {
        showCustomerDialog(null, c -> {
            if (onSuccess != null) onSuccess.run();
        });
    }

    public static void showCustomerDialog(String initialName, Consumer<Customer> onSuccess) {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Customer");
        dialog.setHeaderText("Add a customer without leaving this screen");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = formGrid();
        TextField name = new TextField(initialName != null ? initialName : "");
        TextField phone = new TextField();
        TextField email = new TextField();
        name.setPromptText("Required");
        phone.setPromptText("Optional");
        email.setPromptText("Optional");

        grid.add(new Label("Name *"), 0, 0);  grid.add(name, 1, 0);
        grid.add(new Label("Phone"), 0, 1);   grid.add(phone, 1, 1);
        grid.add(new Label("Email"), 0, 2);   grid.add(email, 1, 2);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(name::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn != saveType) return null;
            if (name.getText() == null || name.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Customer name is required.");
                return null;
            }
            try {
                return insertCustomer(name.getText().trim(),
                        emptyToNull(phone.getText()),
                        emptyToNull(email.getText()));
            } catch (Exception e) {
                log.error("Quick-add customer failed", e);
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(c -> {
            if (c != null) {
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Customer added: " + c.getName());
                if (onSuccess != null) onSuccess.accept(c);
            }
        });
    }

    public static void showSupplierDialog(Runnable onSuccess) {
        showSupplierDialog(null, s -> {
            if (onSuccess != null) onSuccess.run();
        });
    }

    public static void showSupplierDialog(String initialName, Consumer<Supplier> onSuccess) {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Supplier");
        dialog.setHeaderText("Add a supplier without leaving this screen");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = formGrid();
        TextField name = new TextField(initialName != null ? initialName : "");
        TextField phone = new TextField();
        TextField email = new TextField();
        name.setPromptText("Required");

        grid.add(new Label("Name *"), 0, 0);  grid.add(name, 1, 0);
        grid.add(new Label("Phone"), 0, 1);   grid.add(phone, 1, 1);
        grid.add(new Label("Email"), 0, 2);   grid.add(email, 1, 2);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(name::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn != saveType) return null;
            if (name.getText() == null || name.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Supplier name is required.");
                return null;
            }
            try {
                return insertSupplier(name.getText().trim(),
                        emptyToNull(phone.getText()),
                        emptyToNull(email.getText()));
            } catch (Exception e) {
                log.error("Quick-add supplier failed", e);
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(s -> {
            if (s != null) {
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Supplier added: " + s.getName());
                if (onSuccess != null) onSuccess.accept(s);
            }
        });
    }

    public static void showProductDialog(Runnable onSuccess) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Product");
        dialog.setHeaderText("Add a product without leaving this screen");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = formGrid();
        TextField code = new TextField();
        TextField name = new TextField();
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(
                "AC_UNIT", "SPARE_PART", "SERVICE"));
        typeBox.setValue("SPARE_PART");
        TextField sell = new TextField("0.00");
        TextField cost = new TextField("0.00");
        code.setPromptText("Required");
        name.setPromptText("Required");

        grid.add(new Label("Code *"), 0, 0);         grid.add(code, 1, 0);
        grid.add(new Label("Name *"), 0, 1);         grid.add(name, 1, 1);
        grid.add(new Label("Type *"), 0, 2);         grid.add(typeBox, 1, 2);
        grid.add(new Label("Selling Price"), 0, 3);  grid.add(sell, 1, 3);
        grid.add(new Label("Cost Price"), 0, 4);     grid.add(cost, 1, 4);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(code::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn != saveType) return false;
            if (code.getText() == null || code.getText().isBlank()
                    || name.getText() == null || name.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Code and name are required.");
                return false;
            }
            try {
                BigDecimal sp = parseMoney(sell.getText());
                BigDecimal cp = parseMoney(cost.getText());
                String pType = typeBox.getValue() != null ? typeBox.getValue() : "SPARE_PART";
                insertProduct(code.getText().trim(), name.getText().trim(), pType, sp, cp);
                return true;
            } catch (Exception e) {
                log.error("Quick-add product failed", e);
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
                return false;
            }
        });

        dialog.showAndWait().ifPresent(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Product added.");
                if (onSuccess != null) onSuccess.run();
            }
        });
    }

    // ---- DB helpers ----

    private static Customer insertCustomer(String name, String phone, String email) throws SQLException {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            boolean hasCode = columnExists(conn, "customers", "customer_code");
            boolean hasStatus = columnExists(conn, "customers", "status");
            boolean hasActive = columnExists(conn, "customers", "is_active");

            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();

            if (hasCode) {
                cols.append("customer_code, ");
                vals.append("?, ");
            }
            cols.append("name, phone, email");
            vals.append("?, ?, ?");

            if (hasStatus) {
                cols.append(", status");
                vals.append(", 'ACTIVE'");
            } else if (hasActive) {
                cols.append(", is_active");
                vals.append(", true");
            }

            String sql = "INSERT INTO customers (" + cols + ") VALUES (" + vals + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                String code = null;
                if (hasCode) {
                    code = nextCode(conn, "customers", "customer_code", "CUS-");
                    ps.setString(i++, code);
                }
                ps.setString(i++, name);
                ps.setString(i++, phone);
                ps.setString(i, email);
                ps.executeUpdate();

                int generatedId = 0;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getInt(1);
                    }
                }

                Customer c = new Customer();
                c.setId(generatedId);
                c.setCustomerCode(code);
                c.setName(name);
                c.setPhone(phone);
                c.setEmail(email);
                return c;
            }
        }
    }

    private static Supplier insertSupplier(String name, String phone, String email) throws SQLException {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            boolean hasCode = columnExists(conn, "suppliers", "supplier_code");
            boolean hasStatus = columnExists(conn, "suppliers", "status");
            boolean hasActive = columnExists(conn, "suppliers", "is_active");

            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();

            if (hasCode) {
                cols.append("supplier_code, ");
                vals.append("?, ");
            }
            cols.append("name, phone, email");
            vals.append("?, ?, ?");

            if (hasStatus) {
                cols.append(", status");
                vals.append(", 'ACTIVE'");
            } else if (hasActive) {
                cols.append(", is_active");
                vals.append(", true");
            }

            String sql = "INSERT INTO suppliers (" + cols + ") VALUES (" + vals + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                String code = null;
                if (hasCode) {
                    code = nextCode(conn, "suppliers", "supplier_code", "SUP-");
                    ps.setString(i++, code);
                }
                ps.setString(i++, name);
                ps.setString(i++, phone);
                ps.setString(i, email);
                ps.executeUpdate();

                int generatedId = 0;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getInt(1);
                    }
                }

                Supplier s = new Supplier();
                s.setId(generatedId);
                s.setSupplierCode(code);
                s.setName(name);
                s.setPhone(phone);
                s.setEmail(email);
                return s;
            }
        }
    }

    private static void insertProduct(String code, String name, String productType,
                                      BigDecimal sell, BigDecimal cost) throws SQLException {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            boolean hasProductCode = columnExists(conn, "products", "product_code");
            boolean hasSku = columnExists(conn, "products", "sku");
            boolean hasSelling = columnExists(conn, "products", "selling_price");
            boolean hasUnitPrice = columnExists(conn, "products", "unit_price");
            boolean hasCost = columnExists(conn, "products", "cost_price");
            boolean hasIsActive = columnExists(conn, "products", "is_active");
            boolean hasActive = columnExists(conn, "products", "active");
            boolean hasProductType = columnExists(conn, "products", "product_type");
            boolean hasUnit = columnExists(conn, "products", "unit");

            String codeCol = hasProductCode ? "product_code" : (hasSku ? "sku" : "code");
            String priceCol = hasSelling ? "selling_price" : (hasUnitPrice ? "unit_price" : "price");

            StringBuilder cols = new StringBuilder(codeCol + ", name, " + priceCol);
            StringBuilder vals = new StringBuilder("?, ?, ?");

            if (hasProductType) {
                cols.append(", product_type");
                vals.append(", ?");
            }
            if (hasUnit) {
                cols.append(", unit");
                vals.append(", 'PCS'");
            }
            if (hasCost) {
                cols.append(", cost_price");
                vals.append(", ?");
            }
            if (hasIsActive) {
                cols.append(", is_active");
                vals.append(", true");
            } else if (hasActive) {
                cols.append(", active");
                vals.append(", true");
            }

            String sql = "INSERT INTO products (" + cols + ") VALUES (" + vals + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, code);
                ps.setString(i++, name);
                ps.setBigDecimal(i++, sell);
                if (hasProductType) {
                    String pt = (productType == null || productType.isBlank()) ? "SPARE_PART" : productType.trim();
                    if (!pt.equals("AC_UNIT") && !pt.equals("SPARE_PART") && !pt.equals("SERVICE")) {
                        pt = "SPARE_PART";
                    }
                    ps.setString(i++, pt);
                }
                if (hasCost) {
                    ps.setBigDecimal(i, cost);
                }
                ps.executeUpdate();
            }
        }
    }

    private static String nextCode(Connection conn, String table, String col, String prefix) {
        String sql = "SELECT " + col + " FROM " + table + " WHERE " + col + " LIKE ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String last = rs.getString(1);
                    if (last != null && last.startsWith(prefix)) {
                        String numPart = last.substring(prefix.length()).replaceAll("[^0-9]", "");
                        if (!numPart.isEmpty()) {
                            int n = Integer.parseInt(numPart) + 1;
                            return String.format("%s%05d", prefix, n);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Code sequence fallback for {}.{}", table, col, e);
        }
        return String.format("%s%05d", prefix, (int) (System.currentTimeMillis() % 100000));
    }

    private static boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        return false;
    }

    private static GridPane formGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(20, 30, 10, 20));
        return g;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(s.trim().replace(",", ""));
    }

    private static void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
