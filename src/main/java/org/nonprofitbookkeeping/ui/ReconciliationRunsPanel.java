package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.BankAccountOption;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ClearedStatePolicy;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.DifferenceView;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.LedgerLineView;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ManualStatementLineCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SessionSummary;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.Snapshot;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StartCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StatementEntryView;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StatementSource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

/** Full bank statement-to-ledger reconciliation workspace. */
public class ReconciliationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TabPane workflowTabs = new TabPane();
    private final ComboBox<BankAccountOption> bankAccountSelect = new ComboBox<>();
    private final DatePicker statementEndDate = new DatePicker(LocalDate.now());
    private final TextField statementEndingBalance = new TextField("0.00");
    private final TextField notes = new TextField();
    private final ComboBox<SessionSummary> sessionSelect = new ComboBox<>();
    private final ToggleGroup policyGroup = new ToggleGroup();
    private final RadioButton warnOnly = policy("Warn only", ClearedStatePolicy.WARN_ONLY);
    private final RadioButton overwrite = policy("Overwrite ledger cleared state", ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE);
    private final RadioButton neverOverwrite = policy("Never overwrite; require manual resolution", ClearedStatePolicy.NEVER_OVERWRITE_REQUIRE_MANUAL);
    private final RadioButton perLine = policy("Decide per imported line", ClearedStatePolicy.DECIDE_PER_IMPORTED_LINE);

    private final Label status = new Label();
    private final Label startBalance = balanceLabel();
    private final Label bookAll = balanceLabel();
    private final Label bookCleared = balanceLabel();
    private final Label statementBalance = balanceLabel();
    private final Label difference = balanceLabel();
    private final Label dateRange = new Label("No reconciliation loaded.");
    private final Label result = new Label("Result: not run");
    private final Label sessionSummary = new Label("No reconciliation session loaded.");

    private final TableView<StatementEntryView> statementTable = new TableView<>();
    private final TableView<LedgerLineView> ledgerTable = new TableView<>();
    private final TableView<DifferenceView> differenceTable = new TableView<>();
    private final TableView<SessionSummary> sessionTable = new TableView<>();

    private final TextField manualDate = new TextField();
    private final TextField manualDescription = new TextField();
    private final TextField manualReference = new TextField();
    private final TextField manualAmount = new TextField();
    private final TextArea csvImportText = new TextArea();
    private final TextArea ofxImportText = new TextArea();
    private final TextArea qifImportText = new TextArea();
    private final TabPane sourceTabs = new TabPane();

    private Snapshot snapshot;

    public ReconciliationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Bank Reconciliation");
        title.getStyleClass().add("panel-title");
        Label subtitle = new Label("Work through setup, statement entry, matching, and final review without crowding the laptop workspace.");
        subtitle.getStyleClass().add("help-text");
        configureSelectors();
        configureTables();
        configureStatementSources();
        configureWorkflowTabs();
        warnOnly.setSelected(true);
        root.setTop(new VBox(6, title, subtitle, sessionSummary, status, new Separator()));
        root.setCenter(workflowTabs);
        loadBankAccountsAndSessions();
    }

    @Override public String title() { return "Bank Reconciliation"; }
    @Override public Node root() { return root; }

    private void configureWorkflowTabs()
    {
        workflowTabs.getTabs().setAll(
                workflowTab("1. Setup", setupPane()),
                workflowTab("2. Statement", statementPane()),
                workflowTab("3. Match", matchPane()),
                workflowTab("4. Review / Save", reviewPane()));
    }

    private Tab workflowTab(String title, Node content)
    {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private Node setupPane()
    {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        bankAccountSelect.setPrefWidth(420);
        sessionSelect.setPrefWidth(420);
        statementEndingBalance.setPrefWidth(150);
        notes.setPrefWidth(420);
        form.addRow(0, new Label("Configured Bank Account"), bankAccountSelect);
        form.addRow(1, new Label("Statement Through Date"), statementEndDate);
        form.addRow(2, new Label("Statement Ending Balance"), statementEndingBalance);
        form.addRow(3, new Label("Saved Session"), sessionSelect);
        form.addRow(4, new Label("Notes"), notes);
        form.addRow(5, new Label("Cleared-State Mismatch Policy"), policyChoices());

        Button load = new Button("Load");
        load.setOnAction(e -> reloadSnapshot());
        Button startNew = new Button("New Reconciliation");
        startNew.setOnAction(e -> startNewSession());
        Button editExisting = new Button("Edit Existing");
        editExisting.setOnAction(e -> editExistingSession());
        HBox actions = new HBox(8, load, startNew, editExisting, nextButton("Next: Statement", 1));

        VBox sessions = new VBox(6, new Label("Saved Reconciliations"), sessionTable);
        VBox.setVgrow(sessionTable, Priority.ALWAYS);
        VBox pane = new VBox(10, new Label("Setup"), form, actions, sessions);
        pane.setPadding(new Insets(8));
        VBox.setVgrow(sessions, Priority.ALWAYS);
        return pane;
    }

    private HBox policyChoices()
    {
        return new HBox(10, warnOnly, overwrite, neverOverwrite, perLine);
    }

    private Node statementPane()
    {
        VBox pane = new VBox(8,
                new Label("Statement Source"),
                new Label("Add manual statement lines or import statement text from CSV, OFX, or QIF before moving to matching."),
                sourceTabs,
                sourceActions(),
                new HBox(8, backButton("Back: Setup", 0), nextButton("Next: Match", 2)));
        pane.setPadding(new Insets(8));
        VBox.setVgrow(sourceTabs, Priority.ALWAYS);
        return pane;
    }

    private Node matchPane()
    {
        VBox statementPane = new VBox(6, new Label("Statement Entries"), statementTable);
        VBox ledgerPane = new VBox(6, new Label("Ledger Bank-Account Lines"), ledgerTable);
        VBox.setVgrow(statementTable, Priority.ALWAYS);
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        SplitPane split = new SplitPane(statementPane, ledgerPane);
        split.setDividerPositions(0.50);
        VBox pane = new VBox(8,
                new Label("Match Statement Entries to Ledger Lines"),
                matchingActions(),
                split,
                new HBox(8, backButton("Back: Statement", 1), nextButton("Next: Review", 3)));
        pane.setPadding(new Insets(8));
        VBox.setVgrow(split, Priority.ALWAYS);
        return pane;
    }

    private Node reviewPane()
    {
        VBox reportPane = new VBox(6, new Label("Comparison Report"), differenceTable);
        VBox.setVgrow(differenceTable, Priority.ALWAYS);
        Button saveUnresolved = new Button("Save Unresolved");
        saveUnresolved.setOnAction(e -> save(false));
        Button finalize = new Button("Finalize");
        finalize.setOnAction(e -> save(true));
        VBox pane = new VBox(10,
                new Label("Review and Save"),
                balances(),
                reportPane,
                new HBox(8, backButton("Back: Match", 2), saveUnresolved, finalize));
        pane.setPadding(new Insets(8));
        VBox.setVgrow(reportPane, Priority.ALWAYS);
        return pane;
    }

    private Button backButton(String text, int tabIndex)
    {
        Button button = new Button(text);
        button.setOnAction(e -> workflowTabs.getSelectionModel().select(tabIndex));
        return button;
    }

    private Button nextButton(String text, int tabIndex)
    {
        Button button = new Button(text);
        button.setOnAction(e -> workflowTabs.getSelectionModel().select(tabIndex));
        return button;
    }

    private HBox balances()
    {
        HBox cards = new HBox(12,
                card("Beginning Balance", startBalance),
                card("Book Balance — All Transactions", bookAll),
                card("Book Balance — Cleared Only", bookCleared),
                card("Statement Ending Balance", statementBalance),
                card("Difference", difference),
                new VBox(4, dateRange, result));
        cards.setMinHeight(80);
        return cards;
    }

    private HBox sourceActions()
    {
        Button addManual = new Button("Add Manual Line");
        addManual.setOnAction(e -> addManualLine());
        Button importPasted = new Button("Import Pasted Text");
        importPasted.setOnAction(e -> importPastedText());
        Button importFile = new Button("Import File");
        importFile.setOnAction(e -> importFile());
        Button validate = new Button("Validate");
        validate.setOnAction(e -> reloadSnapshot());
        Button clearPreview = new Button("Clear Imported Lines");
        clearPreview.setOnAction(e -> selectedImportText().clear());
        return new HBox(8, addManual, importPasted, importFile, validate, clearPreview);
    }

    private HBox matchingActions()
    {
        Button autoMatch = new Button("Auto Match");
        autoMatch.setOnAction(e -> runAction(() -> service().autoMatch(requireSession()), "Auto match complete."));
        Button match = new Button("Match Selected");
        match.setOnAction(e -> runAction(() -> service().matchSelected(requireSession(), selectedStatementId(), selectedSplitId(), perLine.isSelected() || overwrite.isSelected()), "Selected lines matched."));
        Button unmatch = new Button("Unmatch");
        unmatch.setOnAction(e -> runAction(() -> service().unmatchSelected(requireSession(), selectedStatementId(), selectedSplitId()), "Selected match removed."));
        Button markCleared = new Button("Mark Cleared");
        markCleared.setOnAction(e -> runAction(() -> service().markCleared(requireSession(), selectedSplitId()), "Ledger line marked cleared."));
        Button resolve = new Button("Resolve Difference");
        resolve.setOnAction(e -> runAction(() -> service().resolveDifference(requireSession(), selectedStatementId(), selectedSplitId(), "Resolved from reconciliation workspace."), "Difference resolved."));
        return new HBox(8, autoMatch, match, unmatch, markCleared, resolve);
    }

    private void configureStatementSources()
    {
        GridPane manual = new GridPane();
        manual.setHgap(6);
        manual.setVgap(6);
        manual.addRow(0, new Label("Date"), manualDate);
        manual.addRow(1, new Label("Description"), manualDescription);
        manual.addRow(2, new Label("Reference"), manualReference);
        manual.addRow(3, new Label("Amount"), manualAmount);
        sourceTabs.getTabs().setAll(
                new Tab("Manual Entry", manual),
                new Tab("CSV Import", importTab("Paste CSV with date, amount, description, reference columns.", csvImportText)),
                new Tab("OFX Import", importTab("Paste OFX/QFX statement text or import a file.", ofxImportText)),
                new Tab("QIF Import", importTab("Paste QIF statement text or import a file.", qifImportText)));
        sourceTabs.getTabs().forEach(tab -> tab.setClosable(false));
    }

    private Node importTab(String helper, TextArea area)
    {
        area.setPrefRowCount(16);
        return new VBox(6, new Label(helper), area);
    }

    private void configureSelectors()
    {
        bankAccountSelect.setConverter(new StringConverter<>()
        {
            @Override public String toString(BankAccountOption option) { return option == null ? "" : option.label(); }
            @Override public BankAccountOption fromString(String string) { return null; }
        });
        sessionSelect.setConverter(new StringConverter<>()
        {
            @Override public String toString(SessionSummary summary) { return summary == null ? "Start New" : summary.id() + " • " + summary.bankAccountLabel() + " • " + summary.statementEndDate() + " • " + summary.status(); }
            @Override public SessionSummary fromString(String string) { return null; }
        });
    }

    private void configureTables()
    {
        statementTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(statementTable, "Date", v -> string(v.date()), 110);
        column(statementTable, "Description", StatementEntryView::description, 220);
        column(statementTable, "Reference", StatementEntryView::reference, 120);
        column(statementTable, "Amount", v -> money(v.amount()), 100);
        column(statementTable, "Cleared?", StatementEntryView::clearedState, 100);
        column(statementTable, "Match Status", v -> v.matchStatus().name(), 150);
        column(statementTable, "Matched Ledger Line", v -> string(v.matchedLedgerSplitId()), 130);
        column(statementTable, "Resolution", StatementEntryView::resolution, 220);
        ledgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(ledgerTable, "Date", v -> string(v.date()), 110);
        column(ledgerTable, "Payee / Memo", LedgerLineView::memo, 240);
        column(ledgerTable, "Transaction #", LedgerLineView::transactionNumber, 120);
        column(ledgerTable, "Amount", v -> money(v.amount()), 100);
        column(ledgerTable, "Cleared", v -> v.cleared() ? "Y" : "N", 80);
        column(ledgerTable, "Match Status", v -> v.matchStatus().name(), 150);
        column(ledgerTable, "Statement Ref", v -> string(v.matchedStatementLineId()), 120);
        differenceTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(differenceTable, "Category", v -> v.category().name(), 210);
        column(differenceTable, "Ledger Date", v -> string(v.ledgerDate()), 120);
        column(differenceTable, "Statement Date", v -> string(v.statementDate()), 130);
        column(differenceTable, "Ledger Amount", v -> money(v.ledgerAmount()), 130);
        column(differenceTable, "Statement Amount", v -> money(v.statementAmount()), 140);
        column(differenceTable, "Description", DifferenceView::description, 460);
        sessionTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(sessionTable, "Session", v -> String.valueOf(v.id()), 100);
        column(sessionTable, "Account", SessionSummary::bankAccountLabel, 260);
        column(sessionTable, "Through Date", v -> string(v.statementEndDate()), 120);
        column(sessionTable, "Status", v -> v.status().name(), 110);
        column(sessionTable, "Difference", v -> money(v.difference()), 120);
    }

    private <T> void column(TableView<T> table, String title, java.util.function.Function<T, String> extractor, double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(extractor.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
    }

    private void loadBankAccountsAndSessions()
    {
        try
        {
            bankAccountSelect.getItems().setAll(service().listConfiguredBankAccounts(activeCompanyCode()));
            if (!bankAccountSelect.getItems().isEmpty())
            {
                bankAccountSelect.getSelectionModel().selectFirst();
            }
            sessionSelect.getItems().setAll(service().listSessions(activeCompanyCode()));
            sessionTable.getItems().setAll(sessionSelect.getItems());
            status.setText("Loaded configured bank accounts and saved reconciliations.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load bank reconciliation data: " + UiErrors.safeMessage(ex));
        }
    }

    private void startNewSession()
    {
        BankAccountOption account = bankAccountSelect.getValue();
        if (account == null)
        {
            status.setText("Select a configured bank account first.");
            return;
        }
        runAction(() -> service().start(new StartCommand(activeCompanyCode(), account.id(), statementEndDate.getValue(), parseMoney(statementEndingBalance.getText()), selectedPolicy(), notes.getText())), "Started new reconciliation.");
        workflowTabs.getSelectionModel().select(1);
    }

    private void editExistingSession()
    {
        SessionSummary selected = sessionSelect.getValue() == null ? sessionTable.getSelectionModel().getSelectedItem() : sessionSelect.getValue();
        if (selected == null)
        {
            status.setText("Select a saved reconciliation session first.");
            return;
        }
        runAction(() -> service().load(selected.id()), "Loaded reconciliation session " + selected.id() + ".");
        workflowTabs.getSelectionModel().select(1);
    }

    private void reloadSnapshot()
    {
        if (snapshot == null)
        {
            loadBankAccountsAndSessions();
            return;
        }
        runAction(() -> service().load(snapshot.sessionId()), "Reconciliation reloaded.");
    }

    private void addManualLine()
    {
        runAction(() -> service().addManualLine(new ManualStatementLineCommand(requireSession(), LocalDate.parse(manualDate.getText().trim()), parseMoney(manualAmount.getText()), manualDescription.getText(), manualReference.getText())), "Manual statement line added.");
    }

    private void importPastedText()
    {
        runAction(() -> service().importStatementText(new BankReconciliationWorkspaceService.ImportStatementCommand(requireSession(), selectedSource(), selectedSource().name() + " pasted statement", selectedImportText().getText())), "Pasted statement text imported.");
    }

    private void importFile()
    {
        if (snapshot == null)
        {
            status.setText("Start or load a reconciliation session before importing statement lines.");
            return;
        }
        try
        {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import bank statement");
            File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
            if (file == null)
            {
                return;
            }
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            selectedImportText().setText(text);
            runAction(() -> service().importStatementText(new BankReconciliationWorkspaceService.ImportStatementCommand(requireSession(), selectedSource(), file.getName(), text)), "Statement file imported.");
        }
        catch (RuntimeException | java.io.IOException ex)
        {
            status.setText("Could not import statement file: " + UiErrors.safeMessage(ex));
        }
    }

    private void save(boolean finalize)
    {
        runAction(() -> service().save(requireSession(), finalize), finalize ? "Reconciliation finalized if balanced." : "Unresolved reconciliation saved.");
    }

    private void runAction(java.util.function.Supplier<Snapshot> action, String success)
    {
        try
        {
            apply(action.get());
            status.setText(success);
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
    }

    private void apply(Snapshot next)
    {
        snapshot = next;
        statementTable.getItems().setAll(next.statementEntries());
        ledgerTable.getItems().setAll(next.ledgerLines());
        differenceTable.getItems().setAll(next.differences());
        sessionTable.getItems().setAll(next.savedSessions());
        sessionSelect.getItems().setAll(next.savedSessions());
        startBalance.setText(money(next.balances().beginningBalance()));
        bookAll.setText(money(next.balances().bookBalanceAllTransactions()));
        bookCleared.setText(money(next.balances().bookBalanceClearedOnly()));
        statementBalance.setText(money(next.balances().statementEndingBalance()));
        difference.setText(money(next.balances().difference()));
        dateRange.setText("Period: " + next.statementStartDate() + " – " + next.statementEndDate());
        result.setText(next.differences().isEmpty() && next.balances().difference().compareTo(BigDecimal.ZERO) == 0 ? "Result: Balances Match" : "Result: Unresolved Differences");
        sessionSummary.setText("Session " + next.sessionId() + " • " + next.bankAccountLabel() + " • " + next.statementStartDate() + " – " + next.statementEndDate() + " • " + next.status());
    }

    private long requireSession()
    {
        if (snapshot == null)
        {
            throw new IllegalArgumentException("Start or edit a reconciliation session first.");
        }
        return snapshot.sessionId();
    }

    private Long selectedStatementId()
    {
        StatementEntryView selected = statementTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.statementLineId();
    }

    private Long selectedSplitId()
    {
        LedgerLineView selected = ledgerTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.splitId();
    }

    private TextArea selectedImportText()
    {
        return switch (sourceTabs.getSelectionModel().getSelectedIndex())
        {
            case 2 -> ofxImportText;
            case 3 -> qifImportText;
            default -> csvImportText;
        };
    }

    private StatementSource selectedSource()
    {
        int index = sourceTabs.getSelectionModel().getSelectedIndex();
        return switch (index)
        {
            case 1 -> StatementSource.CSV;
            case 2 -> StatementSource.OFX;
            case 3 -> StatementSource.QIF;
            default -> StatementSource.MANUAL;
        };
    }

    private ClearedStatePolicy selectedPolicy()
    {
        RadioButton selected = (RadioButton) policyGroup.getSelectedToggle();
        return selected == null ? ClearedStatePolicy.WARN_ONLY : (ClearedStatePolicy) selected.getUserData();
    }

    private RadioButton policy(String label, ClearedStatePolicy policy)
    {
        RadioButton button = new RadioButton(label);
        button.setToggleGroup(policyGroup);
        button.setUserData(policy);
        return button;
    }

    private Node card(String title, Label value)
    {
        Label label = new Label(title);
        label.getStyleClass().add("help-text");
        VBox card = new VBox(3, label, value);
        card.getStyleClass().add("dashboard-card");
        card.setPrefWidth(180);
        return card;
    }

    private static Label balanceLabel()
    {
        Label label = new Label("$0.00");
        label.getStyleClass().add("dashboard-value");
        return label;
    }

    private static String money(BigDecimal value)
    {
        return value == null ? "" : "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal parseMoney(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
    }

    private static String string(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private static BankReconciliationWorkspaceService service()
    {
        return UiServiceRegistry.bankReconciliationWorkspace();
    }
}
