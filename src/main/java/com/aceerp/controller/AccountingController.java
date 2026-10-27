package com.aceerp.controller;

import com.aceerp.model.Account;
import com.aceerp.model.JournalEntry;
import com.aceerp.model.JournalEntryLine;
import com.aceerp.service.AccountingService;
import com.aceerp.util.CurrencyFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AccountingController {

    private static final Logger logger = LoggerFactory.getLogger(AccountingController.class);

    // ---- Chart of Accounts Tab ----
    @FXML private TableView<Account> tblAccounts;
    @FXML private TableColumn<Account, String> colAccountCode;
    @FXML private TableColumn<Account, String> colAccountName;
    @FXML private TableColumn<Account, String> colAccountType;
    @FXML private TableColumn<Account, BigDecimal> colAccountBalance;

    // ---- Journal Entries Tab ----
    @FXML private TableView<JournalEntry> tblJournalEntries;
    @FXML private TableColumn<JournalEntry, String> colEntryNumber;
    @FXML private TableColumn<JournalEntry, LocalDate> colEntryDate;
    @FXML private TableColumn<JournalEntry, String> colEntryRef;
    @FXML private TableColumn<JournalEntry, String> colEntryDesc;
    @FXML private TableColumn<JournalEntry, BigDecimal> colEntryTotal;

    @FXML private TableView<JournalEntryLine> tblJournalLines;
    @FXML private TableColumn<JournalEntryLine, String> colLineAccountCode;
    @FXML private TableColumn<JournalEntryLine, String> colLineAccountName;
    @FXML private TableColumn<JournalEntryLine, String> colLineDesc;
    @FXML private TableColumn<JournalEntryLine, BigDecimal> colLineDebit;
    @FXML private TableColumn<JournalEntryLine, BigDecimal> colLineCredit;

    private final AccountingService accountingService = new AccountingService();
    private final ObservableList<Account> accountList = FXCollections.observableArrayList();
    private final ObservableList<JournalEntry> entryList = FXCollections.observableArrayList();
    private final ObservableList<JournalEntryLine> lineList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupAccountsTable();
        setupJournalEntriesTable();
        setupJournalLinesTable();

        loadAccounts();
        loadJournalEntries();
    }

    private void setupAccountsTable() {
        if (tblAccounts == null) return;

        colAccountCode.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        colAccountName.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        colAccountType.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        
        colAccountBalance.setCellValueFactory(new PropertyValueFactory<>("currentBalance"));
        colAccountBalance.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CurrencyFormatter.format(item));
                }
            }
        });

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
            colEntryTotal.setCellFactory(tc -> new TableCell<>() {
                @Override
                protected void updateItem(BigDecimal item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(CurrencyFormatter.format(item));
                    }
                }
            });
        }

        tblJournalEntries.setItems(entryList);
        tblJournalEntries.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                loadJournalLines(newSel.getId());
            } else {
                lineList.clear();
            }
        });
    }

    private void setupJournalLinesTable() {
        if (tblJournalLines == null) return;

        colLineAccountCode.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        colLineAccountName.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        colLineDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

        colLineDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colLineDebit.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.compareTo(BigDecimal.ZERO) == 0) {
                    setText("");
                } else {
                    setText(CurrencyFormatter.format(item));
                }
            }
        });

        colLineCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colLineCredit.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.compareTo(BigDecimal.ZERO) == 0) {
                    setText("");
                } else {
                    setText(CurrencyFormatter.format(item));
                }
            }
        });

        tblJournalLines.setItems(lineList);
    }

    @FXML
    public void handleRefresh() {
        loadAccounts();
        loadJournalEntries();
    }

    private void loadAccounts() {
        try {
            List<Account> accounts = accountingService.listAllAccounts();
            accountList.setAll(accounts);
        } catch (Exception e) {
            logger.error("Failed to load accounts: {}", e.getMessage(), e);
        }
    }

    private void loadJournalEntries() {
        try {
            List<JournalEntry> entries = accountingService.listJournalEntries();
            entryList.setAll(entries);
        } catch (Exception e) {
            logger.error("Failed to load journal entries: {}", e.getMessage(), e);
        }
    }

    private void loadJournalLines(int entryId) {
        try {
            List<JournalEntryLine> lines = accountingService.getLinesForEntry(entryId);
            lineList.setAll(lines);
        } catch (Exception e) {
            logger.error("Failed to load journal lines for entry {}: {}", entryId, e.getMessage(), e);
        }
    }
}