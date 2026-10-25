    package com.aceerp.controller;

    import com.aceerp.model.SearchResult;
    import com.aceerp.model.User;
    import com.aceerp.model.UserSession;
    import com.aceerp.service.AuthService;
    import com.aceerp.service.NavigationService;
    import com.aceerp.service.NavigationService.NavItem;
    import com.aceerp.service.SearchService;
    import com.aceerp.util.AlertHelper;
    import com.aceerp.util.SceneRouter;
    import com.aceerp.util.ViewManager;

    import javafx.application.Platform;
    import javafx.fxml.FXML;
    import javafx.geometry.Insets;
    import javafx.geometry.Pos;
    import javafx.scene.Parent;
    import javafx.scene.control.Hyperlink;
    import javafx.scene.control.Label;
    import javafx.scene.control.TextField;
    import javafx.scene.input.KeyCode;
    import javafx.scene.layout.BorderPane;
    import javafx.scene.layout.HBox;
    import javafx.scene.layout.Priority;
    import javafx.scene.layout.StackPane;
    import javafx.scene.layout.VBox;

    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    import java.io.IOException;
    import java.util.List;
    import java.util.Map;
    import java.util.Timer;
    import java.util.TimerTask;

    public class MainController {

        private static final Logger logger = LoggerFactory.getLogger(MainController.class);

        @FXML private BorderPane rootPane;
        @FXML private VBox sidebar;
        @FXML private StackPane contentArea;
        @FXML private Label statusLabel;
        @FXML private Label userInfoLabel;
        @FXML private Hyperlink logoutLink;
        @FXML private TextField searchField;
        @FXML private VBox searchResultsBox;

        private final NavigationService navigationService = new NavigationService();
        private final SearchService searchService = new SearchService();
        private final ViewManager viewManager = new ViewManager();
        private final AuthService authService = new AuthService();

        private Timer searchDebounceTimer;
        private static final long SEARCH_DEBOUNCE_MS = 300;

        @FXML
        public void initialize() {
            logger.info("Main controller initializing...");

            updateUserInfo();
            buildSidebar();
            setupSearchBar();
            navigateTo("dashboard");

            statusLabel.setText("Welcome, " + UserSession.getInstance().getCurrentUser().getFullName());
            logger.info("Main controller initialized.");
        }

        private void updateUserInfo() {
            User currentUser = UserSession.getInstance().getCurrentUser();
            if (currentUser != null) {
                userInfoLabel.setText(currentUser.getFullName() + " (" + currentUser.getRole().getDisplayName() + ")");
            }
        }

        @FXML
        private void onLogout() {
            boolean confirmed = AlertHelper.confirm("Log Out", "Are you sure you want to log out?");
            if (!confirmed) return;

            try {
                authService.logout();
                viewManager.clearCache();
                SceneRouter.getInstance().switchTo("login.fxml", "ACE ERP — Login", 480, 580);
            } catch (IOException e) {
                logger.error("Logout failed: {}", e.getMessage(), e);
                AlertHelper.error("Error", "Could not return to login screen.");
            }
        }

        private void buildSidebar() {
            if (sidebar.getChildren().size() > 1) {
                sidebar.getChildren().subList(1, sidebar.getChildren().size()).clear();
            }

            Map<String, NavItem> items = navigationService.getVisibleNavItems();

            for (Map.Entry<String, NavItem> entry : items.entrySet()) {
                String key = entry.getKey();
                NavItem item = entry.getValue();

                Label navLabel = new Label(item.icon() + "  " + item.label());
                navLabel.getStyleClass().add("sidebar-item");
                navLabel.setMaxWidth(Double.MAX_VALUE);
                navLabel.setOnMouseClicked(event -> navigateTo(key));

                sidebar.getChildren().add(navLabel);
            }
        }

        private void navigateTo(String moduleKey) {
            if (!navigationService.canAccess(moduleKey)) {
                AlertHelper.warning("Access Denied", "You do not have permission to view this module.");
                return;
            }

            NavItem item = navigationService.getNavItem(moduleKey);
            if (item == null) {
                logger.warn("Unknown navigation key: {}", moduleKey);
                return;
            }

            try {
                Parent view = viewManager.loadView(item.fxmlPath());
                contentArea.getChildren().clear();
                contentArea.getChildren().add(view);
                updateActiveNav(moduleKey);
                statusLabel.setText(item.label() + " module loaded.");
                logger.info("Navigated to: {}", moduleKey);
            } catch (IOException e) {
                logger.error("Failed to load view for '{}': {}", moduleKey, e.getMessage());
                showContentError("Could not load " + item.label() + " module.");
            }
        }

        private void updateActiveNav(String moduleKey) {
            int index = 0;
            for (Map.Entry<String, NavItem> entry : navigationService.getVisibleNavItems().entrySet()) {
                int childIndex = index + 1; // skip heading
                if (childIndex < sidebar.getChildren().size()) {
                    Label label = (Label) sidebar.getChildren().get(childIndex);
                    if (entry.getKey().equals(moduleKey)) {
                        if (!label.getStyleClass().contains("sidebar-item-active")) {
                            label.getStyleClass().add("sidebar-item-active");
                        }
                    } else {
                        label.getStyleClass().remove("sidebar-item-active");
                    }
                }
                index++;
            }
        }

        private void showContentError(String message) {
            VBox errorBox = new VBox(10);
            errorBox.setAlignment(Pos.CENTER);
            Label errorIcon = new Label("⚠");
            errorIcon.setStyle("-fx-font-size: 48px;");
            Label errorLabel = new Label(message);
            errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #e74c3c;");
            errorBox.getChildren().addAll(errorIcon, errorLabel);
            contentArea.getChildren().clear();
            contentArea.getChildren().add(errorBox);
        }

        // ======================= Search =======================
        private void setupSearchBar() {
            searchField.textProperty().addListener((obs, old, newVal) -> onSearchTextChanged(newVal));

            searchField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    searchField.clear();
                    hideSearchResults();
                    contentArea.requestFocus();
                } else if (event.getCode() == KeyCode.ENTER) {
                    cancelDebounceTimer();
                    performSearch(searchField.getText());
                }
            });

            searchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    new Timer(true).schedule(new TimerTask() {
                        @Override public void run() {
                            Platform.runLater(() -> hideSearchResults());
                        }
                    }, 200);
                }
            });

            hideSearchResults();
        }

        private void onSearchTextChanged(String text) {
            cancelDebounceTimer();
            if (text == null || text.trim().length() < 2) {
                hideSearchResults();
                return;
            }
            searchDebounceTimer = new Timer(true);
            searchDebounceTimer.schedule(new TimerTask() {
                @Override public void run() {
                    Platform.runLater(() -> performSearch(text));
                }
            }, SEARCH_DEBOUNCE_MS);
        }

        private void cancelDebounceTimer() {
            if (searchDebounceTimer != null) {
                searchDebounceTimer.cancel();
                searchDebounceTimer = null;
            }
        }

        private void performSearch(String queryText) {
            List<SearchResult> results = searchService.search(queryText);
            searchResultsBox.getChildren().clear();

            if (results.isEmpty()) {
                Label noResults = new Label("No results found for \"" + queryText + "\"");
                noResults.getStyleClass().add("search-no-results");
                noResults.setPadding(new Insets(10, 15, 10, 15));
                searchResultsBox.getChildren().add(noResults);
            } else {
                String currentCategory = "";
                for (SearchResult result : results) {
                    if (!result.category().equals(currentCategory)) {
                        currentCategory = result.category();
                        Label categoryLabel = new Label(currentCategory);
                        categoryLabel.getStyleClass().add("search-category-header");
                        categoryLabel.setPadding(new Insets(8, 15, 4, 15));
                        categoryLabel.setMaxWidth(Double.MAX_VALUE);
                        searchResultsBox.getChildren().add(categoryLabel);
                    }
                    searchResultsBox.getChildren().add(createSearchResultRow(result));
                }
            }
            showSearchResults();
        }

        private HBox createSearchResultRow(SearchResult result) {
            HBox row = new HBox(10);
            row.getStyleClass().add("search-result-item");
            row.setPadding(new Insets(6, 15, 6, 15));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setCursor(javafx.scene.Cursor.HAND);

            Label icon = new Label(result.icon());
            icon.setStyle("-fx-font-size: 16px;");

            VBox textBox = new VBox(2);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            Label title = new Label(result.title());
            title.getStyleClass().add("search-result-title");

            Label subtitle = new Label(result.subtitle());
            subtitle.getStyleClass().add("search-result-subtitle");

            textBox.getChildren().addAll(title, subtitle);
            row.getChildren().addAll(icon, textBox);

            row.setOnMouseClicked(event -> {
                searchField.clear();
                hideSearchResults();
                navigateTo(result.moduleKey());
            });

            return row;
        }

        private void showSearchResults() {
            searchResultsBox.setVisible(true);
            searchResultsBox.setManaged(true);
        }

        private void hideSearchResults() {
            searchResultsBox.setVisible(false);
            searchResultsBox.setManaged(false);
        }
    }