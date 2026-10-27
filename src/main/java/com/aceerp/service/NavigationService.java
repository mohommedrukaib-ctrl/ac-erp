package com.aceerp.service;

import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class NavigationService {

    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);

    public record NavItem(String label, String icon, String fxmlPath, UserRole minRole) {}

    private final Map<String, NavItem> navItems = new LinkedHashMap<>();

    public NavigationService() {
        // Core & Sales Cycle
        navItems.put("dashboard",        new NavItem("Dashboard",         "🏠", "dashboard.fxml",         UserRole.TECHNICIAN));
        navItems.put("customers",        new NavItem("Customers",         "👤", "customers.fxml",         UserRole.USER));
        navItems.put("suppliers",        new NavItem("Suppliers",         "🏭", "suppliers.fxml",         UserRole.USER));
        navItems.put("products",         new NavItem("Products",          "📦", "products.fxml",          UserRole.USER));
        navItems.put("inventory",        new NavItem("Inventory",         "🏬", "inventory.fxml",         UserRole.USER));
        navItems.put("quotations",       new NavItem("Quotations",        "📋", "quotations.fxml",        UserRole.USER));
        navItems.put("sales_orders",     new NavItem("Sales Orders",      "📦", "sales_orders.fxml",      UserRole.USER));
        navItems.put("delivery_notes",   new NavItem("Delivery Notes",    "🚚", "delivery_notes.fxml",    UserRole.USER));
        navItems.put("invoices",         new NavItem("Invoices",          "📄", "invoices.fxml",          UserRole.USER));

        // Purchasing Cycle
        navItems.put("purchases",        new NavItem("Purchases",         "🛒", "purchase_orders.fxml",   UserRole.USER));
        navItems.put("GRN",              new NavItem("GRN",              "🚚", "GRN.fxml",               UserRole.USER));
        navItems.put("supplier_bills",   new NavItem("Supplier Bills",    "📑", "supplier_bills.fxml",    UserRole.USER));
        navItems.put("supplier_payments",new NavItem("Supplier Payments", "💳", "supplier_payments.fxml", UserRole.USER));
        navItems.put("purchase_returns", new NavItem("Purchase Returns",  "↩️", "purchase_returns.fxml",  UserRole.USER));

        // Operations, Financials & Analytics
        navItems.put("services",         new NavItem("Services",          "🔧", "services.fxml",          UserRole.TECHNICIAN));
        navItems.put("accounting",       new NavItem("Accounting",        "💰", "accounting.fxml",        UserRole.MANAGER));
        navItems.put("reports",          new NavItem("Reports",           "📊", "reports.fxml",           UserRole.MANAGER));
        navItems.put("users",            new NavItem("Users",             "👥", "user-management.fxml",  UserRole.ADMIN));
        navItems.put("settings",         new NavItem("Settings",          "⚙",  "settings.fxml",          UserRole.ADMIN));
    }

    public Map<String, NavItem> getVisibleNavItems() {
        Map<String, NavItem> visible = new LinkedHashMap<>();
        UserSession session = UserSession.getInstance();

        if (!session.isLoggedIn()) {
            return visible;
        }

        UserRole currentRole = session.getCurrentUser().getRole();
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            if (currentRole.hasAtLeast(entry.getValue().minRole())) {
                visible.put(entry.getKey(), entry.getValue());
            }
        }
        return visible;
    }

    public NavItem getNavItem(String key) {
        return navItems.get(key);
    }

    public boolean canAccess(String key) {
        NavItem item = navItems.get(key);
        if (item == null) return false;
        return UserSession.getInstance().hasRole(item.minRole());
    }
}