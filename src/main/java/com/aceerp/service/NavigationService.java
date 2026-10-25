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
        navItems.put("dashboard",    new NavItem("Dashboard",    "🏠", "dashboard.fxml",    UserRole.TECHNICIAN));
        navItems.put("customers",    new NavItem("Customers",    "👤", "customers.fxml",    UserRole.USER));
        navItems.put("suppliers",    new NavItem("Suppliers",    "🏭", "suppliers.fxml",    UserRole.USER));
        navItems.put("products",     new NavItem("Products",     "📦", "products.fxml",     UserRole.USER));
        navItems.put("inventory",    new NavItem("Inventory",    "🏬", "inventory.fxml",    UserRole.USER));
        navItems.put("quotations",   new NavItem("Quotations",   "📋", "quotations.fxml",   UserRole.USER));
        navItems.put("sales_orders", new NavItem("Sales Orders", "📦", "sales_orders.fxml", UserRole.USER));
        navItems.put("invoices",     new NavItem("Invoices",     "📄", "invoices.fxml",     UserRole.USER));
        navItems.put("purchases",    new NavItem("Purchases",    "🛒", "placeholder.fxml",  UserRole.USER));
        navItems.put("services",     new NavItem("Services",     "🔧", "placeholder.fxml",  UserRole.TECHNICIAN));
        navItems.put("accounting",   new NavItem("Accounting",   "💰", "placeholder.fxml",  UserRole.MANAGER));
        navItems.put("reports",      new NavItem("Reports",      "📊", "placeholder.fxml",  UserRole.MANAGER));
        navItems.put("users",        new NavItem("Users",        "👥", "user-management.fxml", UserRole.ADMIN));
        navItems.put("settings",     new NavItem("Settings",     "⚙",  "settings.fxml",     UserRole.ADMIN));
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