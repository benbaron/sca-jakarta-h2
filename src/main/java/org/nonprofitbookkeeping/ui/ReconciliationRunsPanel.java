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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SuccessorCommand;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Full bank statement-to-ledger reconciliation workspace. */
public class ReconciliationRunsPanel implements AppPanel
{
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
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
    private final Button addManualButton = new Button("Add Manual Line");
    private final Button importBankStatementButton = new Button("Import Bank Statement…");
    private final Button autoMatchButton = new Button("Auto Match");
    private final Button matchButton = new Button("Match Selected");
    private final Button unmatchButton = new Button("Unmatch");
    private final Button markClearedButton = new Button("Mark Cleared");
    private final Button explainDifferenceButton = new Button("Record Difference Explanation");
    private final Button saveUnresolvedButton = new Button("Save Unresolved");
    private final Button finalizeButton = new Button("Finalize");
    private final Button successorButton = new Button("Start Successor Reconciliation");
    private final TextField differenceExplanation = new TextField();
    private final DatePicker successorEndDate = new DatePicker(LocalDate.now());
    private final TextField successorEndingBalance = new TextField("0.00");
    private final TextField successorActor = new TextField();
    private final TextField successorReason = new TextField();

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
        configureWorkflowTabs();
        companyFormat.install(statementEndDate);
        companyFormat.install(successorEndDate);
        warnOnly.setSelected(true);
        configureMutationActions();
        root.setTop(new VBox(6, title, subtitle, sessionSummary, status, new Separator()));
        root.setCenter(workflowTabs);
        loadBankAccountsAndSessions();
    }

    @Override
    public void onPanelShown()
    {
        String context = DrillThroughCoordinator.consumeContext(AppPanelId.RECONCILIATION_RUNS);
        var returnSession = BankImportNavigationContext.parseReconciliationReturn(context);
        if (returnSession.isPresent())
        {
            try
            {
                apply(service().load(returnSession.getAsLong()));
                status.setText("Bank import committed; reconciliation refreshed from durable bank-review facts.");
            }
            catch (RuntimeException ex)
            {
                status.setText("Could not refresh reconciliation after bank import: " + UiErrors.safeMessage(ex));
            }
            return;
        }
        if (snapshot == null)
        {
            loadBankAccountsAndSessions();
        }
        else
        {
            reloadSnapshot();
        }
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
        VBox setup = new VBox(10, new Label("Setup"), form, actions);
        SplitPane split = new SplitPane(setup, sessions);
        split.setId("reconciliationSetupSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.48);
        CompanySplitPaneStateBinder.bind(split, "reconciliation-setup", 0.48);
        VBox pane = new VBox(split);
        pane.setPadding(new Insets(8));
        VBox.setVgrow(split, Priority.ALWAYS);
        return pane;
    }

    private HBox policyChoices()
    {
        return new HBox(10, warnOnly, overwrite, neverOverwrite, perLine);
    }

    private Node statementPane()
    {
        GridPane manual = new GridPane();
        manual.setHgap(6);
        manual.setVgap(6);
        manual.addRow(0, new Label("Date"), manualDate);
        manual.addRow(1, new Label("Description"), manualDescription);
        manual.addRow(2, new Label("Reference"), manualReference);
        manual.addRow(3, new Label("Amount"), manualAmount);

        Label importHelp = new Label(
                "File imports use the governed Import Preview workspace for OFX/QFX, mapped CSV, and normalized CSV. "
                        + "QIF is not a supported production import format.");
        importHelp.getStyleClass().add("help-text");
        VBox pane = new VBox(8,
                new Label("Statement Source"),
                new Label("Enter a manual statement line here, or open the governed bank-statement import workflow."),
                manual,
                new HBox(8, addManualButton, importBankStatementButton),
                importHelp,
                new HBox(8, backButton("Back: Setup", 0), nextButton("Next: Match", 2)));
        pane.setPadding(new Insets(8));
        return pane;
    }

    private Node matchPane()
    {
        VBox statementPane = new VBox(6, new Label("Statement Entries"), statementTable);
        VBox ledgerPane = new VBox(6, new Label("Ledger Bank-Account Lines"), ledgerTable);
        VBox.setVgrow(statementTable, Priority.ALWAYS);
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        SplitPane split = new SplitPane(statementPane, ledgerPane);
        split.setId("reconciliationMatchSplit");
        split.setDividerPositions(0.50);
        CompanySplitPaneStateBinder.bind(split, "reconciliation-match", 0.50);
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
        saveUnresolvedButton.setOnAction(e -> save(false));
        finalizeButton.setOnAction(e -> save(true));
        VBox summary = new VBox(10, new Label("Review and Save"), balances(), successorPane());
        SplitPane split = new SplitPane(summary, reportPane);
        split.setId("reconciliationReviewSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.28);
        CompanySplitPaneStateBinder.bind(split, "reconciliation-review", 0.28);
        VBox pane = new VBox(10, split,
                new HBox(8, backButton("Back: Match", 2), saveUnresolvedButton, finalizeButton));
        pane.setPadding(new Insets(8));
        VBox.setVgrow(split, Priority.ALWAYS);
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

    private HBox matchingActions()
    {
        differenceExplanation.setPromptText("Explain the reconciliation difference; no accounting entry is created");
        differenceExplanation.setPrefWidth(320);
        Label explanationHelp = new Label("Explanation records a reconciliation fact only; it does not create or change a Journal transaction.");
        explanationHelp.getStyleClass().add("help-text");
        VBox explanation = new VBox(3, new HBox(8, explainDifferenceButton, differenceExplanation), explanationHelp);
        return new HBox(8, autoMatchButton, matchButton, unmatchButton, markClearedButton, explanation);
    }

    private void configureMutationActions()
    {
        addManualButton.setOnAction(e -> addManualLine());
        importBankStatementButton.setOnAction(e -> openGovernedBankImport());
        autoMatchButton.setOnAction(e -> runAction(
                () -> service().autoMatch(requireSession()), "Auto match complete."));
        matchButton.setOnAction(e -> runAction(
                () -> service().matchSelected(requireSession(), selectedStatementId(), selectedSplitId(),
                        perLine.isSelected() || overwrite.isSelected()),
                "Selected lines matched."));
        unmatchButton.setOnAction(e -> runAction(
                () -> service().unmatchSelected(requireSession(), selectedStatementId(), selectedSplitId()),
                "Selected match removed."));
        markClearedButton.setOnAction(e -> runAction(
                () -> service().markCleared(requireSession(), selectedSplitId()),
                "Ledger line marked cleared."));
        explainDifferenceButton.setOnAction(e -> runAction(
                () -> service().recordDifferenceExplanation(requireSession(), selectedStatementId(), selectedSplitId(),
                        differenceExplanation.getText()),
                "Difference explanation recorded; no accounting transaction was created."));
        successorButton.setOnAction(e -> startSuccessor());
    }

    private Node successorPane()
    {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.addRow(0, new Label("Successor Through Date"), successorEndDate);
        form.addRow(1, new Label("Successor Ending Balance"), successorEndingBalance);
        form.addRow(2, new Label("Actor"), successorActor);
        form.addRow(3, new Label("Reason"), successorReason);
        Label help = new Label("Finalized sessions are immutable. Start a successor to continue reconciliation without changing the finalized predecessor.");
        help.getStyleClass().add("help-text");
        return new VBox(6, new Separator(), new Label("Continue After Finalization"), help, form, successorButton);
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
            @Override public String toString(SessionSummary summary) { return summary == null ? "Start New" : summary.id() + " • " + summary.bankAccountLabel() + " • " + companyFormat.formatDate(summary.statementEndDate()) + " • " + summary.status(); }
            @Override public SessionSummary fromString(String string) { return null; }
        });
    }

    private void configureTables()
    {
        statementTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(statementTable, "Date", v -> companyFormat.formatDate(v.date()), 110);
        column(statementTable, "Description", StatementEntryView::description, 220);
        column(statementTable, "Reference", StatementEntryView::reference, 120);
        column(statementTable, "Amount", v -> money(v.amount()), 100);
        column(statementTable, "Cleared?", StatementEntryView::clearedState, 100);
        column(statementTable, "Match Status", v -> v.matchStatus().name(), 150);
        column(statementTable, "Matched Ledger Line", v -> string(v.matchedLedgerSplitId()), 130);
        column(statementTable, "Resolution", StatementEntryView::resolution, 220);
        ledgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(ledgerTable, "Date", v -> companyFormat.formatDate(v.date()), 110);
        column(ledgerTable, "Payee / Memo", LedgerLineView::memo, 240);
        column(ledgerTable, "Transaction #", LedgerLineView::transactionNumber, 120);
        column(ledgerTable, "Amount", v -> money(v.amount()), 100);
        column(ledgerTable, "Cleared", v -> v.cleared() ? "Y" : "N", 80);
        column(ledgerTable, "Match Status", v -> v.matchStatus().name(), 150);
        column(ledgerTable, "Statement Ref", v -> string(v.matchedStatementLineId()), 120);
        differenceTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(differenceTable, "Category", v -> v.category().name(), 210);
        column(differenceTable, "Ledger Date", v -> companyFormat.formatDate(v.ledgerDate()), 120);
        column(differenceTable, "Statement Date", v -> companyFormat.formatDate(v.statementDate()), 130);
        column(differenceTable, "Ledger Amount", v -> money(v.ledgerAmount()), 130);
        column(differenceTable, "Statement Amount", v -> money(v.statementAmount()), 140);
        column(differenceTable, "Description", DifferenceView::description, 460);
        sessionTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(sessionTable, "Session", v -> String.valueOf(v.id()), 100);
        column(sessionTable, "Account", SessionSummary::bankAccountLabel, 260);
        column(sessionTable, "Through Date", v -> companyFormat.formatDate(v.statementEndDate()), 120);
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
        workflowTabs.getSelectionModel().select(
                snapshot != null && snapshot.status() == BankReconciliationWorkspaceService.SessionStatus.FINALIZED ? 3 : 1);
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
        runAction(() -> service().addManualLine(new ManualStatementLineCommand(requireSession(), requireManualDate(), parseMoney(manualAmount.getText()), manualDescription.getText(), manualReference.getText())), "Manual statement line added.");
    }

    private void openGovernedBankImport()
    {
        if (snapshot == null)
        {
            status.setText("Start or load a reconciliation session before importing a bank statement.");
            return;
        }
        DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.IMPORT_PREVIEW,
                BankImportNavigationContext.forReconciliation(snapshot.bankAccountId(), snapshot.sessionId()));
    }

    private void save(boolean finalize)
    {
        runAction(() -> service().save(requireSession(), finalize),
                finalize ? "Reconciliation finalized." : "Reconciliation saved without finalization.");
    }

    private void startSuccessor()
    {
        long predecessor = requireSession();
        runAction(() -> service().startSuccessor(new SuccessorCommand(
                        predecessor,
                        successorEndDate.getValue(),
                        parseMoney(successorEndingBalance.getText()),
                        selectedPolicy(),
                        notes.getText(),
                        successorActor.getText(),
                        successorReason.getText())),
                "Started successor reconciliation; finalized predecessor remains read-only.");
        workflowTabs.getSelectionModel().select(1);
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
        dateRange.setText("Period: " + companyFormat.formatDate(next.statementStartDate()) + " – " + companyFormat.formatDate(next.statementEndDate()));
        result.setText(next.differences().isEmpty() && next.balances().difference().compareTo(BigDecimal.ZERO) == 0 ? "Result: Balances Match" : "Result: Unresolved Differences");
        sessionSummary.setText("Session " + next.sessionId() + " • " + next.bankAccountLabel() + " • " + companyFormat.formatDate(next.statementStartDate()) + " – " + companyFormat.formatDate(next.statementEndDate()) + " • " + next.status());
        successorEndDate.setValue(next.statementEndDate().plusMonths(1));
        successorEndingBalance.setText(companyFormat.formatMoney(next.balances().statementEndingBalance()));
        applyFinalizedReadOnly(next.status() == BankReconciliationWorkspaceService.SessionStatus.FINALIZED);
    }

    private void applyFinalizedReadOnly(boolean finalized)
    {
        Tooltip tooltip = new Tooltip("Finalized reconciliation is read-only. Start a successor reconciliation to continue.");
        for (Button button : java.util.List.of(
                addManualButton, importBankStatementButton,
                autoMatchButton, matchButton, unmatchButton, markClearedButton,
                explainDifferenceButton, saveUnresolvedButton, finalizeButton))
        {
            button.setDisable(finalized);
            button.setTooltip(finalized ? tooltip : null);
        }
        differenceExplanation.setDisable(finalized);
        successorButton.setDisable(!finalized);
        successorEndDate.setDisable(!finalized);
        successorEndingBalance.setDisable(!finalized);
        successorActor.setDisable(!finalized);
        successorReason.setDisable(!finalized);
        if (finalized)
        {
            status.setText("Finalized reconciliation is read-only. Start a successor reconciliation to continue.");
        }
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

    private Label balanceLabel()
    {
        Label label = new Label(companyFormat.formatMoney(BigDecimal.ZERO));
        label.getStyleClass().add("dashboard-value");
        return label;
    }

    private String money(BigDecimal value)
    {
        return value == null ? "" : companyFormat.formatMoney(value);
    }

    private BigDecimal parseMoney(String raw)
    {
        BigDecimal parsed = companyFormat.parseMoney(raw);
        if (parsed == null)
        {
            throw new IllegalArgumentException("Enter a valid money amount.");
        }
        return parsed;
    }

    private LocalDate requireManualDate()
    {
        LocalDate parsed = companyFormat.parseDate(manualDate.getText());
        if (parsed == null)
        {
            throw new IllegalArgumentException("Enter a valid statement date.");
        }
        manualDate.setText(companyFormat.formatDate(parsed));
        return parsed;
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
