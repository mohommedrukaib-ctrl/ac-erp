package com.aceerp.controller;

import com.aceerp.model.Account;
import com.aceerp.model.JournalEntry;
import com.aceerp.model.JournalEntryLine;
import com.aceerp.service.AccountingService;
import com.aceerp.service.AccountingService.TrialBalanceRow;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AccountingController {

    private static final Logger logger = LoggerFactory.getLogger(AccountingController.class);

    // Chart of Accounts
    @FXML private TableView<Account> tblAccounts;
    @FXML private TableColumn<Account, String> colAccountCode;
    @FXML private TableColumn<Account, String> colAccountName;
    @FXML private TableColumn<Account, String> colAccountType;
    @FXML private TableColumn<Account, BigDecimal> colAccountBalance;

    // Journal headers
    @FXML private TableView<JournalEntry> tblJournalEntries;
    @FXML private TableColumn<JournalEntry, String> colEntryNumber;
    @FXML private TableColumn<JournalEntry, LocalDate> colEntryDate;
    @FXML private TableColumn<JournalEntry, String> colEntryRef;
    @FXML private TableColumn<JournalEntry, String> colEntryDesc;
    @FXML private TableColumn<JournalEntry, BigDecimal> colEntryTotal;

    // Journal lines
    @FXML private TableView<JournalEntryLine> tblJournalLines;
    @FXML private TableColumn<JournalEntryLine, String> colLineAccountCode;
    @FXML private TableColumn<JournalEntryLine, String> colLineAccountName;
    @FXML private TableColumn<JournalEntryLine, String> colLineDesc;
    @FXML private TableColumn<JournalEntryLine, BigDecimal> colLineDebit;
    @FXML private TableColumn<JournalEntryLine, BigDecimal> colLineCredit;
    @FXML private Label lblTotalDebit;
    @FXML private Label lblTotalCredit;

    // Trial Balance
    @FXML private TableView<TrialBalanceRow> tblTrialBalance;
    @FXML private TableColumn<TrialBalanceRow, String> colTbCode;
    @FXML private TableColumn<TrialBalanceRow, String> colTbName;
    @FXML private TableColumn<TrialBalanceRow, String> colTbType;
    @FXML private TableColumn<TrialBalanceRow, BigDecimal> colTbDebit;
    @FXML private TableColumn<TrialBalanceRow, BigDecimal> colTbCredit;
    @FXML private Label lblTbDebit;
    @FXML private Label lblTbCredit;
    @FXML private Label lblTbStatus;

    private final AccountingService accountingService = new AccountingService();
    private final ObservableList<Account> accountList = FXCollections.observableArrayList();
    private final ObservableList<JournalEntry> entryList = FXCollections.observableArrayList();
    private final ObservableList<JournalEntryLine> lineList = FXCollections.observableArrayList();
    private final ObservableList<TrialBalanceRow> tbList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupAccountsTable();
        setupJournalEntriesTable();
        setupJournalLinesTable();
        setupTrialBalanceTable();
        handleRefresh();
    }

    // ---------- Table Setup ----------

    private void setupAccountsTable() {
        if (tblAccounts == null) return;
        colAccountCode.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        colAccountName.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        colAccountType.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        colAccountBalance.setCellValueFactory(new PropertyValueFactory<>("currentBalance"));
        colAccountBalance.setCellFactory(tc -> moneyCell());
        tblAccounts.setItems(accountList);
    }

    private void setupJournalEntriesTable() {
        if (tblJournalEntries == null) return;
        colEntryNumber.setCellValueFactory(new PropertyValueFactory<>("entryNumber"));
        colEntryDate.setCellValueFactory(new PropertyValueFactory<>("entryDate"));
        colEntryRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colEntryDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        if (colEntryTotal != null) {
            colEntryTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
            colEntryTotal.setCellFactory(tc -> moneyCell());
        }
        tblJournalEntries.setItems(entryList);
        tblJournalEntries.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel != null) loadJournalLines(sel);
            else {
                lineList.clear();
                updateLineTotals(BigDecimal.ZERO, BigDecimal.ZERO);
            }
        });
    }

    private void setupJournalLinesTable() {
        if (tblJournalLines == null) return;
        colLineAccountCode.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        colLineAccountName.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        colLineDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colLineDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colLineDebit.setCellFactory(tc -> moneyCellBlankZero());
        colLineCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colLineCredit.setCellFactory(tc -> moneyCellBlankZero());
        tblJournalLines.setItems(lineList);
    }

    private void setupTrialBalanceTable() {
        if (tblTrialBalance == null) return;
        colTbCode.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        colTbName.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        colTbType.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        colTbDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colTbDebit.setCellFactory(tc -> moneyCellBlankZero());
        colTbCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colTbCredit.setCellFactory(tc -> moneyCellBlankZero());
        tblTrialBalance.setItems(tbList);
    }

    private <S> TableCell<S, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyFormatter.format(item));
            }
        };
    }

    private <S> TableCell<S, BigDecimal> moneyCellBlankZero() {
        return new TableCell<>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.compareTo(BigDecimal.ZERO) == 0) setText("");
                else setText(CurrencyFormatter.format(item));
            }
        };
    }

    // ---------- Loaders ----------

    @FXML
    public void handleRefresh() {
        loadAccounts();
        loadJournalEntries();
        loadTrialBalance();
    }

    private void loadAccounts() {
        try {
            accountList.setAll(accountingService.listAllAccounts());
        } catch (Exception e) {
            logger.error("Load accounts failed: {}", e.getMessage(), e);
        }
    }

    private void loadJournalEntries() {
        try {
            entryList.setAll(accountingService.listJournalEntries());
        } catch (Exception e) {
            logger.error("Load JE failed: {}", e.getMessage(), e);
        }
    }

    private void loadJournalLines(JournalEntry entry) {
        try {
            List<JournalEntryLine> lines = accountingService.getLinesForEntry(entry.getId());
            lineList.setAll(lines);
            BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
            for (JournalEntryLine l : lines) {
                if (l.getDebit() != null) dr = dr.add(l.getDebit());
                if (l.getCredit() != null) cr = cr.add(l.getCredit());
            }
            updateLineTotals(dr, cr);
        } catch (Exception e) {
            logger.error("Load JE lines failed: {}", e.getMessage(), e);
        }
    }

    private void updateLineTotals(BigDecimal dr, BigDecimal cr) {
        if (lblTotalDebit != null) lblTotalDebit.setText(CurrencyFormatter.format(dr));
        if (lblTotalCredit != null) lblTotalCredit.setText(CurrencyFormatter.format(cr));
    }

    private void loadTrialBalance() {
        try {
            List<TrialBalanceRow> rows = accountingService.getTrialBalance();
            tbList.setAll(rows);
            BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
            for (TrialBalanceRow r : rows) {
                if (r.debit() != null) dr = dr.add(r.debit());
                if (r.credit() != null) cr = cr.add(r.credit());
            }
            if (lblTbDebit != null) lblTbDebit.setText(CurrencyFormatter.format(dr));
            if (lblTbCredit != null) lblTbCredit.setText(CurrencyFormatter.format(cr));
            if (lblTbStatus != null) {
                if (dr.compareTo(cr) == 0) {
                    lblTbStatus.setText("● BALANCED");
                    lblTbStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    lblTbStatus.setText("● OUT OF BALANCE");
                    lblTbStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        } catch (Exception e) {
            logger.error("Trial balance load failed: {}", e.getMessage(), e);
        }
    }

    // ---------- New Journal Entry Dialog ----------

    @FXML
    @SuppressWarnings("unchecked")
    public void handleNewJournalEntry() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Journal Entry");
        dialog.setHeaderText("Create a balanced double-entry voucher (Debits must equal Credits)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(780, 520);

        DatePicker dpDate = new DatePicker(LocalDate.now());
        TextField txtMemo = new TextField();
        txtMemo.setPromptText("Memo / description");

        // Summary Labels declared BEFORE column handlers reference them
        Label lblDraftDr = new Label("0.00");
        Label lblDraftCr = new Label("0.00");
        Label lblBalanced = new Label("");

        List<Account> accounts = accountingService.listAllAccounts();
        ObservableList<JournalEntryLine> draftLines = FXCollections.observableArrayList();
        draftLines.add(new JournalEntryLine());
        draftLines.add(new JournalEntryLine());

        TableView<JournalEntryLine> lineTable = new TableView<>(draftLines);
        lineTable.setEditable(true);
        lineTable.setPrefHeight(300);
        VBox.setVgrow(lineTable, Priority.ALWAYS);

        TableColumn<JournalEntryLine, Account> colAcc = new TableColumn<>("Account");
        colAcc.setPrefWidth(260);
        colAcc.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<Account> combo = new ComboBox<>(FXCollections.observableArrayList(accounts));
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setConverter(new StringConverter<>() {
                    @Override public String toString(Account a) { return a == null ? "" : a.toString(); }
                    @Override public Account fromString(String s) { return null; }
                });
                combo.setOnAction(e -> {
                    JournalEntryLine row = getTableView().getItems().get(getIndex());
                    Account sel = combo.getValue();
                    if (row != null && sel != null) {
                        row.setAccountId(sel.getId());
                        row.setAccountCode(sel.getAccountCode());
                        row.setAccountName(sel.getAccountName());
                    }
                });
            }
            @Override protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                JournalEntryLine row = getTableView().getItems().get(getIndex());
                if (row != null && row.getAccountId() > 0) {
                    accounts.stream().filter(a -> a.getId() == row.getAccountId()).findFirst()
                            .ifPresent(combo::setValue);
                }
                setGraphic(combo);
            }
        });

        TableColumn<JournalEntryLine, String> colDr = new TableColumn<>("Debit");
        colDr.setPrefWidth(110);
        colDr.setCellFactory(TextFieldTableCell.forTableColumn());
        colDr.setCellValueFactory(cdf ->
                new SimpleStringProperty(
                        cdf.getValue().getDebit() == null || cdf.getValue().getDebit().signum() == 0
                                ? "" : cdf.getValue().getDebit().toPlainString()));
        colDr.setOnEditCommit(e -> {
            e.getRowValue().setDebit(parseDecimal(e.getNewValue()));
            if (e.getRowValue().getDebit().signum() > 0) e.getRowValue().setCredit(BigDecimal.ZERO);
            lineTable.refresh();
            refreshDraftTotals(draftLines, lblDraftDr, lblDraftCr);
        });

        TableColumn<JournalEntryLine, String> colCr = new TableColumn<>("Credit");
        colCr.setPrefWidth(110);
        colCr.setCellFactory(TextFieldTableCell.forTableColumn());
        colCr.setCellValueFactory(cdf ->
                new SimpleStringProperty(
                        cdf.getValue().getCredit() == null || cdf.getValue().getCredit().signum() == 0
                                ? "" : cdf.getValue().getCredit().toPlainString()));
        colCr.setOnEditCommit(e -> {
            e.getRowValue().setCredit(parseDecimal(e.getNewValue()));
            if (e.getRowValue().getCredit().signum() > 0) e.getRowValue().setDebit(BigDecimal.ZERO);
            lineTable.refresh();
            refreshDraftTotals(draftLines, lblDraftDr, lblDraftCr);
        });

        TableColumn<JournalEntryLine, String> colD = new TableColumn<>("Description");
        colD.setPrefWidth(200);
        colD.setCellFactory(TextFieldTableCell.forTableColumn());
        colD.setCellValueFactory(cdf ->
                new SimpleStringProperty(
                        cdf.getValue().getDescription() != null ? cdf.getValue().getDescription() : ""));
        colD.setOnEditCommit(e -> e.getRowValue().setDescription(e.getNewValue()));

        lineTable.getColumns().addAll(colAcc, colDr, colCr, colD);

        Button btnAddLine = new Button("+ Add Line");
        btnAddLine.setOnAction(e -> {
            draftLines.add(new JournalEntryLine());
            lineTable.refresh();
        });
        Button btnRemoveLine = new Button("− Remove");
        btnRemoveLine.setOnAction(e -> {
            JournalEntryLine sel = lineTable.getSelectionModel().getSelectedItem();
            if (sel != null && draftLines.size() > 2) {
                draftLines.remove(sel);
                refreshDraftTotals(draftLines, lblDraftDr, lblDraftCr);
            }
        });

        GridPane header = new GridPane();
        header.setHgap(10);
        header.setVgap(8);
        header.addRow(0, new Label("Date:"), dpDate, new Label("Memo:"), txtMemo);
        txtMemo.setPrefWidth(360);

        HBox totals = new HBox(15,
                new Label("Total Debit:"), lblDraftDr,
                new Label("Total Credit:"), lblDraftCr,
                lblBalanced);
        totals.setPadding(new Insets(6, 0, 0, 0));

        HBox lineBtns = new HBox(8, btnAddLine, btnRemoveLine);
        VBox box = new VBox(10, header, lineBtns, lineTable, totals);
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            BigDecimal[] t = sumDraft(draftLines);
            if (t[0].compareTo(t[1]) != 0 || t[0].signum() == 0) {
                AlertHelper.warning("Unbalanced Entry",
                        "Debits must equal Credits and be greater than zero.\n" +
                        "Debits: " + CurrencyFormatter.format(t[0]) +
                        "  Credits: " + CurrencyFormatter.format(t[1]));
                ev.consume();
                return;
            }
            for (JournalEntryLine l : draftLines) {
                if (l.getAccountId() <= 0) {
                    AlertHelper.warning("Missing Account", "Every line must have an account selected.");
                    ev.consume();
                    return;
                }
            }
        });

        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            JournalEntry je = new JournalEntry();
            je.setEntryDate(dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now());
            je.setDescription(txtMemo.getText());
            je.setReferenceType("MANUAL");
            je.setStatus("POSTED");
            je.setAutoPosted(false);

            List<JournalEntryLine> clean = new ArrayList<>();
            for (JournalEntryLine l : draftLines) {
                boolean hasAmt = (l.getDebit() != null && l.getDebit().signum() > 0)
                        || (l.getCredit() != null && l.getCredit().signum() > 0);
                if (l.getAccountId() > 0 && hasAmt) clean.add(l);
            }
            je.setLines(clean);

            accountingService.postJournalEntry(je);
            AlertHelper.info("Posted", "Journal entry " + je.getEntryNumber() + " posted successfully.");
            handleRefresh();
        } catch (Exception ex) {
            logger.error("JE post failed: {}", ex.getMessage(), ex);
            AlertHelper.error("Post Failed", ex.getMessage());
        }
    }

    private void refreshDraftTotals(ObservableList<JournalEntryLine> lines, Label dr, Label cr) {
        BigDecimal[] t = sumDraft(lines);
        dr.setText(CurrencyFormatter.format(t[0]));
        cr.setText(CurrencyFormatter.format(t[1]));
    }

    private BigDecimal[] sumDraft(List<JournalEntryLine> lines) {
        BigDecimal d = BigDecimal.ZERO, c = BigDecimal.ZERO;
        for (JournalEntryLine l : lines) {
            if (l.getDebit() != null) d = d.add(l.getDebit());
            if (l.getCredit() != null) c = c.add(l.getCredit());
        }
        return new BigDecimal[]{
                d.setScale(2, RoundingMode.HALF_UP),
                c.setScale(2, RoundingMode.HALF_UP)
        };
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}