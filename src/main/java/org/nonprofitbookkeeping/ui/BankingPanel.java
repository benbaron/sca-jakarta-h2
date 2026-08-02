package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Banking configuration panel for P05-S2. */
public class BankingPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Bank> banks = new TableView<>();
    private final TableView<CompanyBankAccount> bankAccounts = new TableView<>();
    private final ComboBox<Bank> bankSelector = new ComboBox<>();
    private final ComboBox<Account> existingAccountSelector = new ComboBox<>();
    private final ComboBox<BankingDataFormat> importFormat = new ComboBox<>();
    private final ToggleGroup accountMode = new ToggleGroup();
    private final RadioButton useExistingAccount = new RadioButton("Select existing qualifying Chart of Accounts bank account");
    private final RadioButton createAccount = new RadioButton("Create linked Chart of Accounts bank account");
    private final TextField bankName = new TextField();
    private final TextField routingNumber = new TextField();
    private final TextField address = new TextField();
    private final TextField website = new TextField();
    private final TextField contactName = new TextField();
    private final TextField contactPhone = new TextField();
    private final TextField contactEmail = new TextField();
    private final TextField bankNotes = new TextField();
    private final CheckBox bankActive = new CheckBox("Bank active");
    private final TextField accountCode = new TextField();
    private final TextField accountName = new TextField();
    private final TextField maskedAccount = new TextField();
    private final TextField nickname = new TextField();
    private final TextField openingDate = new TextField();
    private final TextField openingBalance = new TextField();
    private final TextField ofxBankId = new TextField();
    private final TextField ofxAccountId = new TextField();
    private final TextField accountNotes = new TextField();
    private final CheckBox accountActive = new CheckBox("Account active");
    private final Button saveBank = new Button("Save Bank");
    private final Button newBank = new Button("New Bank");
    private final Button saveAccount = new Button("Save Bank Account");
    private final Button refresh = new Button("Refresh");
    private final Label status = new Label();
    private final FormDirtyTracker bankDirty;
    private final FormDirtyTracker accountDirty;
    private Long editingBankId;
    private boolean suppressBankSelection;
    private final BankReviewQueryService reviewQuery;
    private final Supplier<String> companyCode;

    public BankingPanel()
    {
        this(UiServiceRegistry.bankReviewQuery(), () ->
                MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
    }

    BankingPanel(BankReviewQueryService reviewQuery, Supplier<String> companyCode)
    {
        this.reviewQuery = Objects.requireNonNull(reviewQuery, "reviewQuery");
        this.companyCode = Objects.requireNonNull(companyCode, "companyCode");
        root.setPadding(new Insets(8));
        Label title = new Label("Banking");
        title.getStyleClass().add("panel-title");
        newBank.setOnAction(event -> onNew());
        saveBank.setOnAction(event -> saveBank());
        saveAccount.setOnAction(event -> saveBankAccount());
        refresh.setOnAction(event -> reloadWithDiscardProtection());

        configureBankTable();
        configureBankAccountTable();
        configureForms();
        bankDirty = new FormDirtyTracker(this::bankSnapshot);
        accountDirty = new FormDirtyTracker(this::accountSnapshot);

        SplitPane split = new SplitPane(bankListPane(), bankAccountPane());
        split.setId("bankingWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.50);
        CompanySplitPaneStateBinder.bind(split, "banking-workspace", 0.50);
        Button importStatement = new Button("Import Bank Statement…");
        importStatement.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.IMPORT_PREVIEW, "Banking: import statement"));
        Button reviewRows = new Button("Review Imported Rows…");
        reviewRows.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.BANK_TRANSACTIONS, "Banking: durable review rows"));
        root.setTop(new VBox(6, title,
                new HBox(8, newBank, saveBank, refresh, importStatement, reviewRows),
                status, new Separator()));
        root.setCenter(split);

        installFormatCorrection();
        clearBankForm();
        clearAccountForm();
        reload();
    }

    @Override public String title() { return "Banking"; }
    @Override public Node root() { return root; }

    @Override
    public void onNew()
    {
        if (!hasUnsavedChanges() || confirmDiscard())
        {
            clearBankForm();
            clearAccountForm();
            status.setText("Create mode: enter a Bank, then save linked bank-account configuration.");
        }
    }

    FormState formStateForTests()
    {
        return new FormState(
                saveBank.isDisable(),
                useExistingAccount.isSelected(),
                createAccount.isSelected(),
                banks.getItems().size(),
                bankAccounts.getItems().size(),
                status.getText());
    }

    private Node bankListPane()
    {
        return tableEditorPane(
                "bankingInstitutionsSplit",
                "Financial institutions",
                banks,
                bankForm(),
                "banking-institutions");
    }

    private Node bankAccountPane()
    {
        return tableEditorPane(
                "bankingAccountsSplit",
                "Configured bank accounts",
                bankAccounts,
                accountForm(),
                "banking-accounts");
    }

    private Node bankForm()
    {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        int row = 0;
        form.add(new Label("Bank name"), 0, row); form.add(bankName, 1, row++);
        form.add(new Label("Routing number"), 0, row); form.add(routingNumber, 1, row++);
        form.add(new Label("Address"), 0, row); form.add(address, 1, row++);
        form.add(new Label("Website"), 0, row); form.add(website, 1, row++);
        form.add(new Label("Contact name"), 0, row); form.add(contactName, 1, row++);
        form.add(new Label("Contact phone"), 0, row); form.add(contactPhone, 1, row++);
        form.add(new Label("Contact email"), 0, row); form.add(contactEmail, 1, row++);
        form.add(new Label("Notes"), 0, row); form.add(bankNotes, 1, row++);
        form.add(bankActive, 1, row);
        for (Node field : List.of(bankName, routingNumber, address, website, contactName, contactPhone, contactEmail, bankNotes))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        return form;
    }

    private Node accountForm()
    {
        useExistingAccount.setToggleGroup(accountMode);
        createAccount.setToggleGroup(accountMode);
        useExistingAccount.setSelected(true);
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        int row = 0;
        form.add(new Label("Bank"), 0, row); form.add(bankSelector, 1, row++);
        form.add(useExistingAccount, 0, row, 2, 1); row++;
        form.add(new Label("Existing account"), 0, row); form.add(existingAccountSelector, 1, row++);
        form.add(createAccount, 0, row, 2, 1); row++;
        form.add(new Label("New account code"), 0, row); form.add(accountCode, 1, row++);
        form.add(new Label("New account name"), 0, row); form.add(accountName, 1, row++);
        form.add(new Label("Masked account #"), 0, row); form.add(maskedAccount, 1, row++);
        form.add(new Label("Nickname"), 0, row); form.add(nickname, 1, row++);
        form.add(new Label("Opening date (yyyy-mm-dd)"), 0, row); form.add(openingDate, 1, row++);
        form.add(new Label("Opening balance"), 0, row); form.add(openingBalance, 1, row++);
        form.add(new Label("Import format"), 0, row); form.add(importFormat, 1, row++);
        form.add(new Label("OFX bank ID"), 0, row); form.add(ofxBankId, 1, row++);
        form.add(new Label("OFX account ID"), 0, row); form.add(ofxAccountId, 1, row++);
        form.add(new Label("Notes"), 0, row); form.add(accountNotes, 1, row++);
        form.add(accountActive, 1, row++);
        form.add(saveAccount, 1, row);
        for (Node field : List.of(
                bankSelector, existingAccountSelector, accountCode, accountName, maskedAccount, nickname,
                openingDate, openingBalance, importFormat, ofxBankId, ofxAccountId, accountNotes))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        return form;
    }

    private void configureBankTable()
    {
        banks.setPlaceholder(new Label("No banks configured. Use the form below to create a financial institution."));
        TableColumn<Bank, String> name = new TableColumn<>("Bank");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        TableColumn<Bank, String> routing = new TableColumn<>("Routing");
        routing.setCellValueFactory(v -> new SimpleStringProperty(nullToBlank(v.getValue().getRoutingNumber())));
        TableColumn<Bank, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().isActive() ? "Y" : "N"));
        banks.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        banks.getColumns().addAll(name, routing, active);
        configureColumn(name, "bankName", 220);
        configureColumn(routing, "routing", 120);
        configureColumn(active, "active", 84);
        banks.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (suppressBankSelection || newRow == null)
            {
                return;
            }
            if (hasUnsavedChanges() && !confirmDiscard())
            {
                suppressBankSelection = true;
                banks.getSelectionModel().select(oldRow);
                suppressBankSelection = false;
                return;
            }
            loadBank(newRow);
        });
    }

    private void configureBankAccountTable()
    {
        bankAccounts.setPlaceholder(new Label("No configured bank accounts."));
        TableColumn<CompanyBankAccount, String> bank = new TableColumn<>("Bank");
        bank.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getBank() == null ? "" : v.getValue().getBank().getName()));
        TableColumn<CompanyBankAccount, String> account = new TableColumn<>("Chart account");
        account.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getAccount() == null ? "" : v.getValue().getAccount().getCode() + " " + v.getValue().getAccount().getName()));
        TableColumn<CompanyBankAccount, String> nick = new TableColumn<>("Nickname");
        nick.setCellValueFactory(v -> new SimpleStringProperty(nullToBlank(v.getValue().getNickname())));
        TableColumn<CompanyBankAccount, String> format = new TableColumn<>("Format");
        format.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getStatementImportFormat())));
        bankAccounts.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bankAccounts.getColumns().addAll(bank, account, nick, format);
        configureColumn(bank, "bank", 180);
        configureColumn(account, "account", 240);
        configureColumn(nick, "nickname", 180);
        configureColumn(format, "format", 100);
    }

    private void configureForms()
    {
        bankSelector.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(Bank bank) { return bank == null ? "" : bank.getName(); }
            @Override public Bank fromString(String string) { return null; }
        });
        existingAccountSelector.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(Account account) { return account == null ? "" : account.getCode() + " — " + account.getName(); }
            @Override public Account fromString(String string) { return null; }
        });
        importFormat.getItems().setAll(BankingDataFormat.values());
        importFormat.setValue(BankingDataFormat.OFX);
        accountActive.setSelected(true);
    }

    private static void configureColumn(TableColumn<?, ?> column, String key, double prefWidth)
    {
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
    }

    private void installFormatCorrection()
    {
        openingDate.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && !openingDate.getText().isBlank())
            {
                try
                {
                    openingDate.setText(parseDate(openingDate.getText()).format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
                catch (RuntimeException ex)
                {
                    status.setText("Opening date needs a valid date such as yyyy-mm-dd or m/d/yyyy.");
                }
            }
        });
        openingBalance.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused)
            {
                try
                {
                    openingBalance.setText(parseMoney(openingBalance.getText()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
                }
                catch (RuntimeException ex)
                {
                    status.setText("Opening balance needs a valid money amount.");
                }
            }
        });
    }

    private void reload()
    {
        String company = activeCompanyCode();
        try
        {
            List<Bank> bankRows = UiServiceRegistry.bankConfiguration().listBanks(company);
            List<CompanyBankAccount> accountRows = UiServiceRegistry.bankConfiguration().listBankAccounts(company);
            List<Account> qualifying = UiServiceRegistry.accountLookup().listPostingAccountsIncludingInactive().stream()
                    .filter(BankingPanel::isQualifyingBankAccount)
                    .toList();
            banks.setItems(FXCollections.observableArrayList(bankRows));
            bankSelector.setItems(FXCollections.observableArrayList(bankRows));
            bankAccounts.setItems(FXCollections.observableArrayList(accountRows));
            existingAccountSelector.setItems(FXCollections.observableArrayList(qualifying));
            BankReviewQueryService.ReviewSummary review = reviewQuery.summary(company);
            status.setText("Loaded " + bankRows.size() + " bank(s), " + accountRows.size()
                    + " configured bank account(s), and " + review.rowCount()
                    + " durable review row(s) in " + review.batchCount() + " batch(es); reviewable="
                    + review.reviewableCount() + ", duplicates=" + review.duplicateCount()
                    + ", errors=" + review.errorCount() + ". Deactivate records to preserve history.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load Banking configuration: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveBank()
    {
        try
        {
            BankCommand command = new BankCommand(activeCompanyCode(), bankName.getText(), routingNumber.getText(), address.getText(), website.getText(), contactName.getText(), contactPhone.getText(), contactEmail.getText(), bankNotes.getText(), bankActive.isSelected());
            Bank saved = editingBankId == null
                    ? UiServiceRegistry.bankConfiguration().createBank(command)
                    : UiServiceRegistry.bankConfiguration().updateBank(editingBankId, command);
            editingBankId = saved.getId();
            bankDirty.markClean();
            status.setText("Saved bank " + saved.getName() + ".");
            reload();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save bank: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveBankAccount()
    {
        try
        {
            Bank selectedBank = bankSelector.getValue();
            if (selectedBank == null)
            {
                throw new IllegalArgumentException("Bank is required.");
            }
            Account account = useExistingAccount.isSelected()
                    ? existingAccountSelector.getValue()
                    : UiServiceRegistry.accountAdmin().upsert(accountCode.getText(), accountName.getText(), AccountType.BANK, NormalBalance.DEBIT, AccountSubtype.CASH, null, true);
            if (account == null)
            {
                throw new IllegalArgumentException("Chart-of-accounts bank account is required.");
            }
            CompanyBankAccount saved = UiServiceRegistry.bankConfiguration().createBankAccount(new BankAccountCommand(
                    activeCompanyCode(), selectedBank.getId(), account.getId(), maskedAccount.getText(), nickname.getText(), parseDate(openingDate.getText()), parseMoney(openingBalance.getText()), importFormat.getValue(), ofxBankId.getText(), ofxAccountId.getText(), accountNotes.getText(), accountActive.isSelected()));
            accountDirty.markClean();
            status.setText("Saved configured bank account " + saved.getName() + ".");
            reload();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save bank account: " + UiErrors.safeMessage(ex));
        }
    }

    private void loadBank(Bank bank)
    {
        if (bank == null)
        {
            return;
        }
        editingBankId = bank.getId();
        bankName.setText(bank.getName());
        routingNumber.setText(nullToBlank(bank.getRoutingNumber()));
        address.setText(nullToBlank(bank.getAddress()));
        website.setText(nullToBlank(bank.getWebsite()));
        contactName.setText(nullToBlank(bank.getContactName()));
        contactPhone.setText(nullToBlank(bank.getContactPhone()));
        contactEmail.setText(nullToBlank(bank.getContactEmail()));
        bankNotes.setText(nullToBlank(bank.getNotes()));
        bankActive.setSelected(bank.isActive());
        bankSelector.setValue(bank);
        status.setText("Edit mode for bank " + bank.getName() + ".");
        bankDirty.markClean();
        accountDirty.markClean();
    }

    private void clearBankForm()
    {
        editingBankId = null;
        banks.getSelectionModel().clearSelection();
        bankName.clear();
        routingNumber.clear();
        address.clear();
        website.clear();
        contactName.clear();
        contactPhone.clear();
        contactEmail.clear();
        bankNotes.clear();
        bankActive.setSelected(true);
        bankDirty.markClean();
    }

    private void clearAccountForm()
    {
        bankSelector.setValue(null);
        useExistingAccount.setSelected(true);
        existingAccountSelector.setValue(null);
        accountCode.clear();
        accountName.clear();
        maskedAccount.clear();
        nickname.clear();
        openingDate.clear();
        openingBalance.setText("0.00");
        importFormat.setValue(BankingDataFormat.OFX);
        ofxBankId.clear();
        ofxAccountId.clear();
        accountNotes.clear();
        accountActive.setSelected(true);
        accountDirty.markClean();
    }

    private Node tableEditorPane(String id,
                                 String tableLabel,
                                 TableView<?> table,
                                 Node form,
                                 String stateKey)
    {
        VBox tableRegion = new VBox(6, new Label(tableLabel), table);
        tableRegion.setPadding(new Insets(8));
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(form);
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane split = new SplitPane(tableRegion, editorScroll);
        split.setId(id);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        CompanySplitPaneStateBinder.bind(split, stateKey, 0.58);
        return split;
    }

    private BankFormSnapshot bankSnapshot()
    {
        return new BankFormSnapshot(bankName.getText(), routingNumber.getText(), address.getText(), website.getText(),
                contactName.getText(), contactPhone.getText(), contactEmail.getText(), bankNotes.getText(), bankActive.isSelected());
    }

    private AccountFormSnapshot accountSnapshot()
    {
        return new AccountFormSnapshot(
                idOf(bankSelector.getValue()), useExistingAccount.isSelected(), idOf(existingAccountSelector.getValue()),
                accountCode.getText(), accountName.getText(), maskedAccount.getText(), nickname.getText(),
                openingDate.getText(), openingBalance.getText(), importFormat.getValue(), ofxBankId.getText(),
                ofxAccountId.getText(), accountNotes.getText(), accountActive.isSelected());
    }

    private static Long idOf(Object value)
    {
        if (value instanceof Bank bank)
        {
            return bank.getId();
        }
        return value instanceof Account account ? account.getId() : null;
    }

    private void reloadWithDiscardProtection()
    {
        if (!hasUnsavedChanges() || confirmDiscard())
        {
            clearBankForm();
            clearAccountForm();
            reload();
        }
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard banking edits");
        confirmation.setHeaderText("Discard unsaved Banking changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static boolean isQualifyingBankAccount(Account account)
    {
        return account.getAccountType() == AccountType.BANK
                && account.getNormalBalance() == NormalBalance.DEBIT
                && account.getSubtype() == AccountSubtype.CASH;
    }

    private String activeCompanyCode()
    {
        return companyCode.get();
    }

    private static LocalDate parseDate(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("M-d-uuuu"),
                DateTimeFormatter.ofPattern("MM/dd/uuuu"),
                DateTimeFormatter.ofPattern("MM-dd-uuuu")))
        {
            try
            {
                return LocalDate.parse(trimmed, formatter);
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next accepted UI date format.
            }
        }
        throw new IllegalArgumentException("Opening date must be a valid date.");
    }

    private static BigDecimal parseMoney(String value)
    {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.trim().replace("$", "").replace(",", ""));
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    record FormState(boolean saveBankDisabled,
                     boolean useExistingAccountSelected,
                     boolean createAccountSelected,
                     int bankCount,
                     int bankAccountCount,
                     String statusText)
    {
    }

    void setBankNameForTests(String value)
    {
        bankName.setText(value);
    }

    @Override public void onSave() { if (bankDirty.isDirty()) saveBank(); else saveBankAccount(); }
    @Override public boolean hasUnsavedChanges() { return bankDirty.isDirty() || accountDirty.isDirty(); }

    private record BankFormSnapshot(
            String name, String routing, String address, String website, String contactName,
            String contactPhone, String contactEmail, String notes, boolean active)
    {
    }

    private record AccountFormSnapshot(
            Long bankId, boolean useExisting, Long existingAccountId, String accountCode, String accountName,
            String maskedAccount, String nickname, String openingDate, String openingBalance,
            BankingDataFormat importFormat, String ofxBankId, String ofxAccountId, String notes, boolean active)
    {
    }
}
