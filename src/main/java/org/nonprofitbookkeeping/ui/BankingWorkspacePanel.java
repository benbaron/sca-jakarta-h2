package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.service.*;
import org.nonprofitbookkeeping.service.BankingWorkspaceQuery.AccountView;
import org.nonprofitbookkeeping.service.BankingWorkspaceQuery.BankItem;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.*;

/** Approved account-scoped Banking workflow over canonical journal and reconciliation services. */
public final class BankingWorkspacePanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final WorkPaneTabs pages = new WorkPaneTabs("bankingWorkspacePages");
    private final WorkPaneTabs reconciliationPages = new WorkPaneTabs("bankingWorkspaceReconciliationPages");
    private final ComboBox<AccountView> account = new ComboBox<>();
    private final TableView<AccountView> accounts = table("bankingWorkspaceAccounts");
    private final TableView<BankItem> journal = table("bankingWorkspaceJournal");
    private final TableView<BankItem> candidates = table("bankingWorkspaceCandidates");
    private final TableView<StatementEntryView> statements = table("bankingWorkspaceStatements");
    private final ComboBox<SessionSummary> history = new ComboBox<>();
    private final Label status = label("Select a configured bank account.");
    private final Label contextLabel = label("");
    private final Label openingAmount = label("Not established");
    private final Label openingSource = label("First statement opening required");
    private final Label openingBook = label("");
    private final Label balanceSummary = label("");
    private final Label importedHeader = label("No imported statement header available.");
    private BankingWorkspaceQuery.StatementHeader suggestedHeader;
    private final Label result = label("No statement selected");
    private final Label bankName = label("");
    private final Label ledgerName = label("");
    private final TextField nickname = field("bankingWorkspaceNickname");
    private final TextField masked = field("bankingWorkspaceMaskedAccount");
    private final CheckBox active = new CheckBox("Account active");
    private final Label entryBank = label("");
    private final Label entryTitle = label("Banking details");
    private final Label entryDate = label("");
    private final TextField reference = field("bankingWorkspaceReference");
    private final CheckBox cleared = new CheckBox("Cleared for this bank account");
    private final Label clearedDate = label("");
    private final DatePicker statementEnd = new DatePicker(LocalDate.now());
    private final TextField statementEnding = field("bankingWorkspaceStatementEnding");
    private final DatePicker firstDate = new DatePicker();
    private final TextField firstAmount = field("bankingWorkspaceFirstOpening");
    private final CheckBox reviewed = new CheckBox("Opening journal position and outstanding items reviewed");
    private final VBox firstOpening = new VBox(7);
    private final TextField manualDescription = field("bankingWorkspaceManualDescription");
    private final TextField manualReference = field("bankingWorkspaceManualReference");
    private final TextField manualAmount = field("bankingWorkspaceManualAmount");
    private final DatePicker manualDate = new DatePicker();
    private final Label pairDifference = label("Select one statement entry and one bank item.");
    private final CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private final Supplier<BankingWorkspaceQuery> query;
    private final Supplier<BankReconciliationWorkspaceService> reconciliation;
    private final Supplier<BankConfigurationService> configuration;
    private final Supplier<TransactionEntryService> entries;
    private final Supplier<String> company;
    private final Supplier<String> database;
    private final Supplier<AppPanel> maintenance;
    private final Supplier<AppPanel> statementTools;
    private final Map<String, Draft> drafts = new HashMap<>();
    private final List<Button> writeButtons = new ArrayList<>();
    private final Button match;
    private final Button unmatch;
    private final Button complete;
    private final Button saveReference;
    private final Button saveAccount;
    private final Button confirmOpening;
    private final Button importStatement;
    private final Button manualStatement;
    private final Button saveProgress;
    private final Button useImported;
    private final Button removeManual;
    private final Button newStatementButton;
    private final Button correctOpeningButton;
    private final Button openJournalButton;
    private AccountView selected;
    private Snapshot snapshot;
    private OpeningView opening;
    private List<BankItem> bankItems = List.of();
    private String loadedScope;
    private boolean showStatementTools;
    private boolean loading;
    private boolean busy;
    private long generation;
    private Runnable capabilitiesChanged = () -> { };
    private Consumer<RunCommandResult> commandFeedback = ignored -> { };

    public BankingWorkspacePanel()
    {
        this(UiServiceRegistry::bankingWorkspaceQuery, UiServiceRegistry::bankReconciliationWorkspace,
             UiServiceRegistry::bankConfiguration, UiServiceRegistry::transactionEntry,
             () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
             () -> MainWindow.sharedSessionState().databaseSelection().activeDatabasePath(),
             BankingPanel::new, BankTransactionsPanel::new);
    }

    BankingWorkspacePanel(Supplier<BankingWorkspaceQuery> query,
                          Supplier<BankReconciliationWorkspaceService> reconciliation,
                          Supplier<BankConfigurationService> configuration,
                          Supplier<TransactionEntryService> entries,
                          Supplier<String> company, Supplier<String> database,
                          Supplier<AppPanel> maintenance, Supplier<AppPanel> statementTools)
    {
        this.query = query;
        this.reconciliation = reconciliation;
        this.configuration = configuration;
        this.entries = entries;
        this.company = company;
        this.database = database;
        this.maintenance = maintenance;
        this.statementTools = statementTools;
        root.setPadding(new Insets(8));
        root.setId("bankingWorkspace");
        account.setId("bankingWorkspaceAccount");
        account.setMaxWidth(Double.MAX_VALUE);
        account.setConverter(new StringConverter<>()
        {
            @Override public String toString(AccountView value)
            {
                return value == null ? "" : value.label();
            }
            @Override public AccountView fromString(String text)
            {
                return null;
            }
        });
        history.setId("bankingWorkspaceHistory");
        history.setMaxWidth(Double.MAX_VALUE);
        history.setConverter(new StringConverter<>()
        {
            @Override public String toString(SessionSummary value)
            {
                return value == null ? "New statement" : format.formatDate(value.statementStartDate()) + " – "
                       + format.formatDate(value.statementEndDate()) + " · " + value.status();
            }
            @Override public SessionSummary fromString(String text)
            {
                return null;
            }
        });
        cleared.setDisable(true);
        statementEnd.setId("bankingWorkspaceStatementEnd");
        firstDate.setId("bankingWorkspaceFirstDate");
        manualDate.setId("bankingWorkspaceManualDate");
        reviewed.setId("bankingWorkspaceOpeningReviewed");
        result.setId("bankingWorkspaceResult");
        openingAmount.setId("bankingWorkspaceOpeningAmount");
        firstOpening.setId("bankingWorkspaceFirstOpeningPane");
        status.setId("bankingWorkspaceStatus");
        entryBank.setId("bankingWorkspaceEntryBank");
        entryDate.setId("bankingWorkspaceEntryDate");
        match = action("Match and clear", "bankingWorkspaceMatch", this::match, ApplicationPermission.BOOKKEEPING_WRITE);
        unmatch = action("Unmatch", "bankingWorkspaceUnmatch", this::unmatch, ApplicationPermission.BOOKKEEPING_WRITE);
        complete = action("Complete reconciliation", "bankingWorkspaceComplete", this::complete,
                          ApplicationPermission.BOOKKEEPING_WRITE);
        saveReference = action("Save banking reference", "bankingWorkspaceSaveReference", this::saveReference,
                               ApplicationPermission.BOOKKEEPING_WRITE);
        saveAccount = action("Save account details", "bankingWorkspaceSaveAccount", this::saveAccount,
                             ApplicationPermission.COMPANY_ADMIN);
        confirmOpening = action("Confirm starting balance", "bankingWorkspaceConfirmOpening", this::confirmOpening,
                                ApplicationPermission.BOOKKEEPING_WRITE);
        importStatement = action("Import bank statement…", "bankingWorkspaceImport", this::importStatement,
                                 ApplicationPermission.BOOKKEEPING_WRITE);
        manualStatement = action("Enter statement manually…", "bankingWorkspaceManual", this::manualStatement,
                                 ApplicationPermission.BOOKKEEPING_WRITE);
        saveProgress = action("Save progress", "bankingWorkspaceSaveProgress", this::saveProgress,
                              ApplicationPermission.BOOKKEEPING_WRITE);
        useImported = action("Use imported statement dates / balance", "bankingWorkspaceUseImported", this::useImportedHeader,
                             ApplicationPermission.BOOKKEEPING_WRITE);
        removeManual = action("Remove manual statement entry", "bankingWorkspaceRemoveManual", this::removeManual,
                              ApplicationPermission.BOOKKEEPING_WRITE);
        newStatementButton=action("New statement", "bankingWorkspaceNewStatement", this::newStatement, ApplicationPermission.BOOKKEEPING_WRITE);
        correctOpeningButton=action("Correct first opening…", "bankingWorkspaceCorrectOpening", this::correctOpening, ApplicationPermission.BOOKKEEPING_WRITE);
        openJournalButton=action("Open complete journal entry", "bankingWorkspaceOpenJournal", this::openJournal, null);
        for (Control input : List.of(nickname,masked,active))
        {
            UiPermissionGate.gate(input,ApplicationPermission.COMPANY_ADMIN,"Edit configured bank account");
        }
        for (Control input : List.of(reference,statementEnd,statementEnding,firstDate,firstAmount,reviewed,manualDate,manualDescription,manualReference,manualAmount))
        {
            UiPermissionGate.gate(input,ApplicationPermission.BOOKKEEPING_WRITE,"Edit banking or statement details");
        }
        configureTables();
        pages.addPane("Accounts", accountsPane()).addPane("Bank journal", journalPane()).addPane("Reconcile",
                reconciliationPane());
        account.setMinWidth(0);
        contextLabel.setMinWidth(0);
        HBox selector = new HBox(8, label("Bank / account"), account);
        HBox.setHgrow(account, Priority.ALWAYS);
        VBox chrome = new VBox(4, selector, contextLabel);
        root.setTop(PanelViewport.section(chrome));
        ResizablePanelHeader.keepCompact(root);
        root.setCenter(pages);
        root.setBottom(status);
        account.valueProperty().addListener((obs, old, value) ->
        {
            if (!loading && value != null)
            {
                capture();
                selected = value;
                loadSelected(null);
            }
        });
        accounts.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
        {
            if (!loading && value != null && (selected == null || value.id() != selected.id())) account.setValue(value);
        });
        journal.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
        {
            if (!loading)
            {
                applyBankItem(value);
            }
        });
        statements.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
        {
            if (!loading && selected != null) draft().statement = value == null ? null : value.statementLineId();
            updateActions();
        });
        candidates.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
        {
            if (!loading && selected != null) draft().candidate = value == null ? null : value.anchorSplitId();
            updateActions();
        });
        history.valueProperty().addListener((obs, old, value) ->
        {
            if (!loading && value != null)
            {
                if (draft().headerDirty || draft().openingDirty)
                {
                    if (new Alert(Alert.AlertType.CONFIRMATION,
                                  "Discard unfinished statement header/opening edits before changing periods?", ButtonType.OK,
                                  ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
                    {
                        loading = true;
                        history.setValue(old);
                        loading = false;
                        return;
                    }
                }
                resetStatementDraft();
                draft().session = value.id();
                loadSelected(value.id());
            }
        });
        pages.getSelectionModel().selectedIndexProperty().addListener((obs, old, value) -> capabilitiesChanged.run());
        nickname.textProperty().addListener((o, a, b)->markAccount());
        masked.textProperty().addListener((o, a, b)->markAccount());
        active.selectedProperty().addListener((o, a, b)->markAccount());
        reference.textProperty().addListener((o, a, b)-> { if (!loading && selected != null && draft().entry != null) draft().references.put(draft().entry, b); });
        statementEnding.textProperty().addListener((o, a, b)-> { if (!loading && selected != null) draft().headerDirty = true; capture(); });
        statementEnd.valueProperty().addListener((o, a, b)-> { if (!loading && selected != null) draft().headerDirty = true; capture(); });
        firstAmount.textProperty().addListener((o, a, b)-> {if (!loading && selected != null)draft().openingDirty = true; capture();});
        firstDate.valueProperty().addListener((o, a, b)-> {if (!loading && selected != null)draft().openingDirty = true; capture();});
        reviewed.selectedProperty().addListener((o, a, b)-> {if (!loading && selected != null)draft().openingDirty = true; capture();});
        for (DatePicker date : List.of(statementEnd, firstDate, manualDate))
        {
            format.install(date);
        }
        for (TextField money : List.of(statementEnding, firstAmount, manualAmount))
        {
            format.installMoney(money);
        }
        FullTextTooltipInstaller.install(root);
        CompanyTableStateBinder.applyProductionPanel(root, AppPanelId.BANKING);
        updateActions();
    }

    private Node accountsPane()
    {
        var details = new GridPane();
        details.setHgap(12);
        details.setVgap(8);
        add(details, 0, "Bank", bankName);
        add(details, 1, "Account nickname", nickname);
        add(details, 2, "Linked ledger account / chart", ledgerName);
        add(details, 3, "Masked account number", masked);
        details.add(active, 1, 4);
        var editor = new VBox(8, label("Selected account details"), details,
                              flow(saveAccount, action("Bank / account settings…", "bankingWorkspaceSettings",
                                      () -> secondary("Bank and account settings", maintenance.get()), null)),
                              label("Deactivate accounts to retain banking history; historical records are not deleted."));
        return vertical(accounts, editor, "banking-workspace-accounts", .58);
    }

    private Node journalPane()
    {
        var details = new GridPane();
        details.setHgap(12);
        details.setVgap(8);
        add(details, 0, "Bank account portion", entryBank);
        add(details, 1, "Transaction / check / deposit #", reference);
        add(details, 2, "Journal transaction date", entryDate);
        details.add(cleared, 1, 3);
        add(details, 4, "Cleared date", clearedDate);
        var editor = new VBox(8, entryTitle, details, flow(saveReference,
                              openJournalButton,
                              action("Reconcile selected account", "bankingWorkspaceGoReconcile", ()->pages.getSelectionModel().select(2), null)),
                              label("Banking is derived from this configured ledger account. Transfers clear separately per bank. Use Open complete journal entry for ledger corrections or deletion."));
        return vertical(journal, editor, "banking-workspace-journal", .62);
    }

    private Node reconciliationPane()
    {
        var fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(7);
        add(fields, 0, "Beginning cleared balance", openingAmount);
        add(fields, 1, "Opening source / date", openingSource);
        add(fields, 2, "Beginning book balance", openingBook);
        add(fields, 3, "Statement ending date", statementEnd);
        add(fields, 4, "Statement ending balance", statementEnding);
        add(fields, 5, "Saved reconciliation for this account", history);
        var first = new GridPane();
        first.setHgap(12);
        first.setVgap(7);
        add(first, 0, "First beginning date", firstDate);
        add(first, 1, "First statement opening balance", firstAmount);
        firstOpening.getChildren().setAll(label("First reconciliation opening — review before confirming"), first, reviewed,
                                          confirmOpening);
        var header = new VBox(8, firstOpening, fields, importedHeader, flow(useImported, correctOpeningButton),
                              flow(importStatement, manualStatement,
                                   newStatementButton,
                                   action("Statements / export…", "bankingWorkspaceStatementTools", ()->secondary("Statement review and export",
                                           statementTools.get()), null),
                                   action("Checks / exceptions…", "bankingWorkspaceChecks", ()->secondary("Checks / exceptions",
                                           new CheckExceptionsPane()), null)));
        var split = new SplitPane(titled("Bank statement entries", statements),
                                  titled("Banking journal entries — selected account only", candidates));
        split.setId("bankingWorkspaceMatchingSplit");
        split.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(split, "banking-workspace-match", .5);
        var body = new BorderPane(split);
        body.setBottom(new VBox(7, pairDifference, flow(match, unmatch, removeManual),
                                label("Reconciliation history and imported sources cannot be deleted. Remove an unlinked manual entry after unmatching; correct ledger entries in Journal."),
                                action("Create missing journal entry from reviewed statement…", "bankingWorkspaceCreateMissing",
                                       ()->secondary("Reviewed statement acceptance", statementTools.get()), ApplicationPermission.BOOKKEEPING_WRITE)));
        reconciliationPages.addPane("Statement setup", header).addPane("Match entries", body);
        VBox pane = new VBox(6, result, balanceSummary, flow(saveProgress, complete), reconciliationPages);
        pane.setMinSize(0, 0);
        VBox.setVgrow(reconciliationPages, Priority.ALWAYS);
        return pane;
    }

    private void configureTables()
    {
        column(accounts, "Bank", "bank", AccountView::bank);
        column(accounts, "Account", "account", AccountView::label);
        column(accounts, "Ledger / chart", "ledger", AccountView::ledgerLabel);
        column(accounts, "Active", "active", a->a.active() ? "Yes" : "No");
        column(accounts, "Statement through", "through", AccountView::statementEnd);
        column(accounts, "Beginning cleared", "opening", AccountView::clearedOpening);
        column(accounts, "Book ending", "book", AccountView::bookBalance);
        column(accounts, "Status", "status", AccountView::status);
        column(accounts, "Difference", "difference", AccountView::difference);
        for (TableView<BankItem> table : List.of(journal, candidates))
        {
            column(table, "Journal entry", "entry", BankItem::transactionId);
            column(table, "Transaction date", "date", BankItem::date);
            column(table, "Description", "memo", BankItem::description);
            column(table, "Check / deposit / transfer #", "reference", BankItem::reference);
            column(table, "Signed amount", "amount", BankItem::amount);
            column(table, "Cleared", "cleared", item->item.mixedClearing() ? "Mixed — review" : item.cleared() ? "Yes" : "No");
            column(table, "Cleared date", "cleared-date", BankItem::clearedOn);
            column(table, "Fund allocations", "allocations", item->item.splitIds().size());
        }
        column(statements, "Bank date", "date", StatementEntryView::date);
        column(statements, "Reference", "reference", StatementEntryView::reference);
        column(statements, "Description", "description", StatementEntryView::description);
        column(statements, "Amount", "amount", StatementEntryView::amount);
        column(statements, "Source status", "source-status", StatementEntryView::clearedState);
        column(statements, "Match", "match", item->item.matchedLedgerSplitId() == null ? "Unmatched" : "Matched");
    }

    void showLegacyDestination(AppPanelId requested)
    {
        if (requested == AppPanelId.RECONCILIATION_RUNS)
        {
            pages.getSelectionModel().select(2);
        }
        if (requested == AppPanelId.BANK_TRANSACTIONS)
        {
            pages.getSelectionModel().select(1);
        }
    }

    @Override public String title()
    {
        return "Banking";
    }
    @Override public Node root()
    {
        return root;
    }
    @Override public void onPanelShown()
    {
        if (Objects.equals(loadedScope, scope()))
        {
            capture();
        }
        if (!Objects.equals(loadedScope, scope()))
        {
            generation++;
            busy = false;
            root.setDisable(false);
            selected = null;
            snapshot = null;
            loadedScope = scope();
            account.getItems().clear();
        }
        String context = DrillThroughCoordinator.consumeContext(AppPanelId.BANKING);
        showStatementTools = "banking:statements".equals(context);
        var request = BankImportNavigationContext.parseReconciliationSession(context);
        if (request.isEmpty())
        {
            request = BankImportNavigationContext.parseReconciliationReturn(context);
        }
        if (request.isPresent())
        {
            long id = request.getAsLong();
            var service = reconciliation.get();
            String owner = company.get();
            run("Load bank reconciliation", ()->
            {
                if (service.listSessions(owner).stream().noneMatch(row->row.id() == id))
                    throw new IllegalArgumentException("Reconciliation belongs to another company or no longer exists.");
                return service.load(id);
            }, value-> { selected = null; pages.getSelectionModel().select(2); reconciliationPages.getSelectionModel().select(1); reload(value.bankAccountId(), id); });
        }
        else
        {
            var returned = BankImportNavigationContext.parseAccountReturn(context);
            if (returned.isPresent())
            {
                pages.getSelectionModel().select(2);
                reconciliationPages.getSelectionModel().select(0);
                reload(returned.getAsLong(), null);
            }
            else
            {
                if ("banking:reconcile".equals(context))
                {
                    pages.getSelectionModel().select(2);
                }
                if ("banking:journal".equals(context))
                {
                    pages.getSelectionModel().select(1);
                }
                reload(selected == null ? null : selected.id(), null);
            }
        }
    }

    private void reload(Long preferred, Long sessionId)
    {
        var service = query.get();
        String owner = company.get();
        run("Load bank accounts", ()->service.accounts(owner, LocalDate.now()), rows->
        {
            loading = true;
            try
            {
                if (preferred != null && rows.stream().noneMatch(row -> row.id() == preferred))
                {
                    throw new IllegalArgumentException("The requested configured bank account is unavailable for this company; select an account explicitly.");
                }
                account.getItems().setAll(rows);
                accounts.getItems().setAll(rows);
                selected = rows.stream().filter(row->Objects.equals(row.id(), preferred)).findFirst()
                .orElseGet(()->rows.stream().filter(AccountView::active).findFirst().orElse(rows.isEmpty() ? null : rows.get(0)));
                account.setValue(selected);
                if (selected != null)
                {
                    accounts.getSelectionModel().select(selected);
                }
            }
            finally
            {
                loading = false;
            }
            if (selected == null)
            {
                status.setText("Configure a bank account in Bank / account settings.");
                updateActions();
            }
            else
            {
                loadSelected(sessionId);
            }
        });
    }

    private void loadSelected(Long requested)
    {
        if (selected == null)
        {
            return;
        }
        long accountId = selected.id();
        String owner = company.get();
        var queries = query.get();
        var service = reconciliation.get();
        Draft draft = draft();
        Long id = requested != null ? requested : draft.session != null ? draft.session : selected.latestSessionId();
        run("Load selected bank account", ()->
        {
            Snapshot current = id == null || id < 0 ? null : service.load(id);
            if (current != null && (!owner.equals(current.companyCode()) || current.bankAccountId() != accountId))
                throw new IllegalArgumentException("Reconciliation does not belong to the selected bank account.");
            OpeningView start = current == null ? null : service.opening(current.sessionId());
            LocalDate end = current == null ? (draft.end == null ? LocalDate.now() : draft.end) : current.statementEndDate();
            return new Loaded(current, start, queries.journal(owner, accountId, LocalDate.of(9999, 12, 31)), service.listSessionsForAccount(owner, accountId), queries.latestStatement(owner, accountId));
        }, this::apply);
    }

    private void apply(Loaded data)
    {
        snapshot = data.snapshot();
        opening = data.opening();
        bankItems = data.journal();
        Draft d = draft();
        suggestedHeader = data.header();
        importedHeader.setText(suggestedHeader == null ?
                               "No imported statement header available; enter dates and ending balance manually."
                               : "Imported statement: " + suggestedHeader.source() + " · through " + format.formatDate(
                                   suggestedHeader.end()) + " · ending " + format.formatMoney(suggestedHeader.ending()));
        if (opening != null && opening.confirmed() && !d.editOpening)
        {
            d.openingDirty=false; d.firstAmount=null; d.firstDate=null; d.reviewed=false;
        }
        loading = true;
        try
        {
            contextLabel.setText(selected.ledgerLabel());
            bankName.setText(selected.bank());
            ledgerName.setText(selected.ledgerLabel());
            nickname.setText(d.accountDirty ? d.nickname : selected.configuration().nickname() == null ? selected.name() :
                             selected.configuration().nickname());
            masked.setText(d.accountDirty ? d.masked : selected.maskedNumber());
            active.setSelected(d.accountDirty ? d.active : selected.active());
            journal.getItems().setAll(bankItems);
            BankItem remembered = bankItems.stream().filter(item->Objects.equals(item.anchorSplitId(),
                                  d.entry)).findFirst().orElse(bankItems.isEmpty() ? null : bankItems.get(0));
            if (remembered != null)
            {
                journal.getSelectionModel().select(remembered);
            }
            applyBankItem(remembered);
            history.getItems().setAll(data.history());
            if (snapshot == null)
            {
                history.setValue(null);
                statements.getItems().clear();
                candidates.getItems().clear();
                candidates.setPlaceholder(label("Start or select a reconciliation in Statement setup to show eligible journal entries."));
                statements.setPlaceholder(label("Start or select a reconciliation in Statement setup, then import or enter bank transactions."));
                statementEnd.setValue(d.end == null ? LocalDate.now() : d.end);
                statementEnding.setText(d.ending == null ? "" : d.ending);
                firstDate.setValue(d.firstDate == null ? selected.configuration().openingDate() : d.firstDate);
                firstAmount.setText(d.firstAmount == null ? "" : d.firstAmount);
                reviewed.setSelected(d.reviewed);
                openingAmount.setText("Not established");
                openingSource.setText("Start a statement for this account to confirm its opening.");
                openingBook.setText("Calculated from the journal when the period is selected.");
                balanceSummary.setText("");
                result.setText("New reconciliation — known statement ending balance required");
            }
            else
            {
                d.session = snapshot.sessionId();
                history.getSelectionModel().select(data.history().stream().filter(row->row.id() ==
                                                   snapshot.sessionId()).findFirst().orElse(null));
                statementEnd.setValue(snapshot.statementEndDate());
                statementEnding.setText(d.headerDirty &&
                                        d.ending != null ? d.ending : format.formatMoney(snapshot.balances().statementEndingBalance()));
                candidates.setPlaceholder(label("No eligible journal entries for this account and statement period."));
                statements.setPlaceholder(label("No bank transactions in this reconciliation. Import or enter them from Statement setup."));
                statements.getItems().setAll(snapshot.statementEntries());
                Set<Long> scopeIds = new HashSet<>();
                snapshot.ledgerLines().forEach(line->scopeIds.add(line.splitId()));
                candidates.getItems().setAll(bankItems.stream().filter(item->item.splitIds().stream().anyMatch(
                                                 scopeIds::contains)).toList());
                statements.getSelectionModel().select(statements.getItems().stream().filter(row -> Objects.equals(row.statementLineId(), d.statement)).findFirst().orElse(null));
                candidates.getSelectionModel().select(candidates.getItems().stream().filter(row -> Objects.equals(row.anchorSplitId(), d.candidate)).findFirst().orElse(null));
                openingAmount.setText(opening.confirmed() ? format.formatMoney(opening.clearedOpening()) : "Not established");
                openingSource.setText(opening.source() + " · beginning " + format.formatDate(opening.startDate()));
                openingBook.setText(format.formatMoney(opening.bookOpening()) + " · journal before " + format.formatDate(
                                        opening.startDate()));
                firstDate.setValue(d.firstDate == null ? opening.startDate() : d.firstDate);
                firstAmount.setText(d.firstAmount == null ? "" : d.firstAmount);
                reviewed.setSelected(d.reviewed);
                boolean balanced = balanced();
                result.setText(snapshot.status() == SessionStatus.FINALIZED ? "Balanced · Completed · Read-only" : balanced ?
                               "Balanced · Ready to complete" : "In progress · " + snapshot.differences().size() + " item(s) require review");
                balanceSummary.setText("Book ending: " + format.formatMoney(snapshot.balances().bookBalanceAllTransactions())
                                       + "     Cleared ending: " + (opening.confirmed() ? format.formatMoney(snapshot.balances().bookBalanceClearedOnly()) :
                                               "Opening required")
                                       + "     Difference: " + (opening.confirmed() ? format.formatMoney(snapshot.balances().difference()) :
                                               "Opening required"));
            }
            firstOpening.setVisible(snapshot != null && opening != null && (!opening.confirmed() || d.editOpening)
                    && snapshot.status() != SessionStatus.FINALIZED);
            firstOpening.setManaged(firstOpening.isVisible());
        }
        finally
        {
            loading = false;
        }
        status.setText(selected.active()
                ? selected.name() + " loaded. Matching affects clearing only; outstanding journal items carry forward."
                : selected.name() + " is inactive. Banking journal and reconciliation are read-only; reactivate it in Accounts to continue.");
        updateActions();
        if (showStatementTools)
        {
            showStatementTools = false;
            javafx.application.Platform.runLater(() -> secondary("Statement review and export", statementTools.get()));
        }
    }

    private void applyBankItem(BankItem item)
    {
        boolean wasLoading = loading;
        loading = true;
        try
        {
            if (item == null)
            {
                entryTitle.setText("Select a bank journal entry");
                entryBank.setText(selected == null ? "" : selected.label()+"\n"+selected.ledgerLabel());
                entryDate.setText("");
                reference.setDisable(true);
                reference.clear();
                cleared.setSelected(false);
                clearedDate.setText("");
                return;
            }
            Draft d = draft();
            entryTitle.setText("Journal #" + item.transactionId() + " · Banking: Yes");
            entryBank.setText(selected.label() + "\n" + selected.ledgerLabel());
            reference.setText(d.references.getOrDefault(item.anchorSplitId(), item.reference()));
            d.entry = item.anchorSplitId();
            entryDate.setText(format.formatDate(item.date()));
            cleared.setSelected(item.cleared());
            clearedDate.setText(item.mixedClearing() ? "Mixed — review Fund allocations" : item.clearedOn() == null ?
                                "Not cleared" : format.formatDate(item.clearedOn()));
            reference.setDisable(!selected.active() || item.cleared() || item.mixedClearing());
        }
        finally
        {
            loading = wasLoading;
            updateActions();
        }
    }

    private void resetStatementDraft()
    {
        Draft d = draft();
        d.statement=null; d.candidate=null; d.editOpening=false;
        d.headerDirty = false;
        d.openingDirty = false;
        d.firstDate = null;
        d.firstAmount = null;
        d.reviewed = false;
        d.ending = null;
        d.end = null;
    }

    private void newStatement()
    {
        if (selected == null)
        {
            return;
        }
        if ((draft().headerDirty || draft().openingDirty) && new Alert(Alert.AlertType.CONFIRMATION,
                "Discard unfinished statement header/opening edits and start another statement?", ButtonType.OK,
                ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return;
        }
        reconciliationPages.getSelectionModel().select(0);
        capture();
        resetStatementDraft();
        Draft d = draft();
        d.session = -1L;
        d.ending = "";
        d.end = LocalDate.now();
        loadSelected(-1L);
        pages.getSelectionModel().select(2);
    }

    private void useImportedHeader()
    {
        if (suggestedHeader == null || snapshot != null)
        {
            return;
        }
        statementEnd.setValue(suggestedHeader.end());
        statementEnding.setText(format.formatMoney(suggestedHeader.ending()));
        firstDate.setValue(suggestedHeader.start());
        capture();
        status.setText("Imported dates and ending balance selected. Save progress to start; confirm a known opening for the first period.");
    }

    private void saveAccount()
    {
        if (selected == null)
        {
            return;
        }
        capture();
        AccountView view = selected;
        var old = view.configuration();
        var service = configuration.get();
        String owner = company.get();
        var command = new BankAccountCommand(owner, old.bankId(), old.accountId(), masked.getText(), nickname.getText(),
                                             old.openingDate(), old.openingBalance(), old.statementImportFormat(), old.ofxBankId(), old.ofxAccountId(), old.notes(),
                                             active.isSelected());
        run("Save bank account", ()->service.updateBankAccount(view.id(), command), saved-> {draft().accountDirty = false; reload(view.id(), snapshot == null ? null : snapshot.sessionId());});
    }

    private void saveReference()
    {
        BankItem item = journal.getSelectionModel().getSelectedItem();
        if (item == null)
        {
            return;
        }
        long bank = selected.id();
        String expected = item.reference(), value = reference.getText();
        var service = entries.get();
        run("Save banking reference", ()->service.updateBankingReference(bank, item.anchorSplitId(), expected, value), saved-> {draft().references.remove(item.anchorSplitId()); loadSelected(null);});
    }

    private void saveProgress()
    {
        if (selected == null)
        {
            return;
        }
        capture();
        var service = reconciliation.get();
        String owner = company.get();
        long bank = selected.id();
        BigDecimal ending = money(statementEnding.getText());
        LocalDate end = statementEnd.getValue();
        if (snapshot == null)
        {
            run("Start reconciliation", ()->service.start(new StartCommand(owner, bank, end, ending,
                    ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE, null)), saved-> {draft().headerDirty = false; draft().session = saved.sessionId(); reload(bank, saved.sessionId());});
        }
        else
        {
            long id = snapshot.sessionId();
            run("Save reconciliation progress", ()->service.saveStatement(id, ending, false), saved-> {draft().headerDirty = false; reload(selected.id(), id);});
        }
    }

    private void correctOpening()
    {
        if (opening == null || !opening.confirmed() || opening.sourceSessionId() != null) return;
        Draft d=draft(); d.editOpening=true;
        firstDate.setValue(opening.startDate());
        firstAmount.setText(format.formatMoney(opening.clearedOpening()));
        reviewed.setSelected(false);
        firstOpening.setVisible(true); firstOpening.setManaged(true);
        status.setText("Review and confirm the corrected first opening. Unmatch statement items first; inherited openings remain read-only.");
        updateActions();
    }

    private void confirmOpening()
    {
        if (snapshot == null)
        {
            return;
        }
        capture();
        var service = reconciliation.get();
        long id = snapshot.sessionId();
        LocalDate start = firstDate.getValue();
        BigDecimal amount = money(firstAmount.getText());
        boolean checked = reviewed.isSelected();
        run("Confirm statement opening", ()->service.confirmOpening(id, start, amount, checked), saved-> {draft().openingDirty = false; draft().editOpening=false; reload(selected.id(), id);});
    }

    private void match()
    {
        var statement = statements.getSelectionModel().getSelectedItem();
        var item = candidates.getSelectionModel().getSelectedItem();
        if (snapshot == null || statement == null || item == null)
        {
            return;
        }
        long id = snapshot.sessionId();
        var service = reconciliation.get();
        run("Match and clear", ()->service.matchBankItem(id, statement.statementLineId(), item.anchorSplitId()),
            saved->reload(selected.id(),id));
    }

    private void unmatch()
    {
        var statement = statements.getSelectionModel().getSelectedItem();
        var item = candidates.getSelectionModel().getSelectedItem();
        if (snapshot == null || statement == null || item == null)
        {
            return;
        }
        long id = snapshot.sessionId();
        var service = reconciliation.get();
        run("Unmatch bank item", ()->service.unmatchBankItem(id, statement.statementLineId(), item.anchorSplitId()),
            saved->reload(selected.id(),id));
    }

    private void complete()
    {
        if (snapshot == null)
        {
            return;
        }
        if (new Alert(Alert.AlertType.CONFIRMATION,
                      "Complete this account's reconciliation through " + format.formatDate(snapshot.statementEndDate()) +
                      "? The completed period becomes read-only.", ButtonType.OK,
                      ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return;
        }
        long id = snapshot.sessionId();
        BigDecimal ending = money(statementEnding.getText());
        var service = reconciliation.get();
        run("Complete reconciliation", ()->service.saveStatement(id, ending, true), saved-> {draft().headerDirty = false; reload(selected.id(), id);});
    }

    private void manualStatement()
    {
        if (snapshot == null)
        {
            status.setText("Enter the statement ending balance and Save progress to start this account's statement first.");
            return;
        }
        long id = snapshot.sessionId();
        Map<Long, ManualDraft> manualDrafts = draft().manual;
        ManualDraft pending = manualDrafts.computeIfAbsent(id, ignored -> new ManualDraft());
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Manual statement entry — " + selected.label());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        manualDate.setValue(pending.date == null ? snapshot.statementEndDate() : pending.date);
        manualDescription.setText(pending.description == null ? "" : pending.description);
        manualReference.setText(pending.reference == null ? "" : pending.reference);
        manualAmount.setText(pending.amount == null ? "" : pending.amount);
        add(grid, 0, "Bank date", manualDate);
        add(grid, 1, "Reference / check / deposit #", manualReference);
        add(grid, 2, "Description", manualDescription);
        add(grid, 3, "Signed amount", manualAmount);
        dialog.getDialogPane().setContent(PanelViewport.section(grid));
        FullTextTooltipInstaller.install(dialog.getDialogPane());
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
        {
            pending.date = manualDate.getValue();
            pending.description = manualDescription.getText();
            pending.reference = manualReference.getText();
            pending.amount = manualAmount.getText();
            pending.dirty = true;
            var service = reconciliation.get();
            var command = new ManualStatementLineCommand(id, manualDate.getValue(), money(manualAmount.getText()),
                    manualDescription.getText(), manualReference.getText());
            run("Save manual bank statement entry", ()->service.addManualLine(command), saved ->
            {
                manualDrafts.remove(id);
                reload(selected.id(), id);
            });
        }
        else
        {
            manualDrafts.remove(id);
        }
    }

    private void removeManual()
    {
        var statement = statements.getSelectionModel().getSelectedItem();
        if (snapshot == null || statement == null)
        {
            return;
        }
        if (new Alert(Alert.AlertType.CONFIRMATION,
                      "Remove this manual statement entry from the reconciliation? Its source and audit history will be retained.",
                      ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return;
        }
        long id = snapshot.sessionId();
        var service = reconciliation.get();
        run("Remove manual statement entry", ()->service.removeManualLine(id, statement.statementLineId()),
            saved->reload(selected.id(),id));
    }

    private void importStatement()
    {
        if (selected == null)
        {
            return;
        }
        capture();
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.IMPORT_PREVIEW, snapshot == null
                ? BankImportNavigationContext.forAccount(selected.id())
                : BankImportNavigationContext.forReconciliation(selected.id(), snapshot.sessionId()));
    }

    private void openJournal()
    {
        var item = journal.getSelectionModel().getSelectedItem();
        if (item != null)
        {
            DrillThroughCoordinator.openTransactionEditorWithContext("Edit transaction Txn #" + item.transactionId());
        }
    }

    private void secondary(String title, AppPanel panel)
    {
        capture();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setResizable(true);
        if (root.getScene() != null && root.getScene().getWindow() != null) dialog.initOwner(root.getScene().getWindow());
        PanelViewport.prepare(panel.root(), "banking-secondary-" + title);
        dialog.setTitle(title + (selected == null ? "" : " — " + selected.label()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (selected != null)
        {
            if (panel instanceof BankingPanel settings && !title.equals("New bank account"))
            {
                settings.selectConfiguredAccount(selected.id(), false);
            }
            if (panel instanceof BankTransactionsPanel tools)
            {
                tools.pinConfiguredAccount(selected.id());
                tools.showStatementReview();
            }
            if (panel instanceof CheckExceptionsPane checks)
            {
                checks.pinConfiguredAccount(selected.id());
            }
        }
        if (panel instanceof BankTransactionsPanel tools)
        {
            tools.showStatementReview();
        }
        dialog.getDialogPane().setContent(panel.root());
        dialog.getDialogPane().setPrefSize(1000, 730);
        FullTextTooltipInstaller.install(dialog.getDialogPane());
        dialog.setOnCloseRequest(event ->
        {
            if (panel.hasUnsavedChanges() && new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved changes in " + title + "?", ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) event.consume();
        });
        panel.onPanelShown();
        dialog.showAndWait();
        reload(selected == null ? null : selected.id(), snapshot == null ? null : snapshot.sessionId());
    }

    private void updateActions()
    {
        boolean mutable = selected != null && selected.active() && snapshot != null &&
                          snapshot.status() != SessionStatus.FINALIZED;
        boolean known = opening != null && opening.confirmed();
        var st = statements.getSelectionModel().getSelectedItem();
        var item = candidates.getSelectionModel().getSelectedItem();
        boolean pair = st != null && item != null;
        BigDecimal difference = pair ? st.amount().subtract(item.amount()) : null;
        pairDifference.setText(pair ? "Selected difference: " + format.formatMoney(difference) :
                               "Select one statement entry and one bank item.");
        match.setDisable(busy || !mutable || !known || !pair || difference.signum() != 0 || st.matchedLedgerSplitId() != null ||
                         item.statementLineId() != null || draft().references.containsKey(item.anchorSplitId()));
        unmatch.setDisable(busy || !mutable || !pair || item.statementLineId() == null ||
                           !Objects.equals(item.statementLineId(), st.statementLineId()));
        complete.setDisable(busy || !mutable || !balanced() || selected != null && draft().editOpening);
        removeManual.setDisable(busy || !mutable || st == null || !st.manual() || st.matchedLedgerSplitId() != null);
        boolean editingOpening=selected!=null && draft().editOpening;
        confirmOpening.setDisable(busy || !mutable || known && !editingOpening);
        firstDate.setDisable(busy || !mutable || known && !editingOpening);
        firstAmount.setDisable(busy || !mutable || known && !editingOpening);
        reviewed.setDisable(busy || !mutable || known && !editingOpening);
        correctOpeningButton.setDisable(busy || !mutable || !known || opening.sourceSessionId()!=null
                || snapshot.statementEntries().stream().anyMatch(row->row.matchedLedgerSplitId()!=null));
        statementEnd.setDisable(busy || snapshot != null);
        statementEnding.setDisable(busy || snapshot != null && !mutable);
        useImported.setDisable(busy || snapshot != null || suggestedHeader == null || selected == null || !selected.active());
        importStatement.setDisable(busy || selected == null || !selected.active() || snapshot != null && !mutable);
        manualStatement.setDisable(busy || !mutable);
        saveProgress.setDisable(busy || selected == null || !selected.active() || snapshot != null && !mutable);
        var selectedItem = journal.getSelectionModel().getSelectedItem();
        openJournalButton.setDisable(busy || selectedItem == null);
        saveReference.setDisable(busy || selected == null || !selected.active() || selectedItem == null || selectedItem.cleared() || selectedItem.mixedClearing());
        saveAccount.setDisable(busy || selected == null);
        newStatementButton.setDisable(busy || selected == null || !selected.active());
        account.setDisable(busy);
        history.setDisable(busy);
        capabilitiesChanged.run();
    }

    private boolean balanced()
    {
        return snapshot != null && opening != null && opening.confirmed() &&
               snapshot.balances().statementEndingBalance() != null
               && snapshot.balances().difference().signum() == 0 && snapshot.differences().isEmpty()
               && snapshot.statementEntries().stream().allMatch(line->line.matchedLedgerSplitId() != null);
    }

    private <T> void run(String operation, Supplier<T> work, Consumer<T> success)
    {
        if (busy)
        {
            return;
        }
        busy = true;
        root.setDisable(true);
        long token = ++generation;
        String scope = scope();
        status.setText(operation + "…");
        updateActions();
        UiAsync.run("banking-workspace", work, value->
        {
            if (token != generation || !scope.equals(scope()))return;
            busy = false;
            root.setDisable(false);
            try
            {
                success.accept(value);
                commandFeedback.accept(new RunCommandResult(true, operation + " completed."));
            }
            catch (RuntimeException ex)
            {
                status.setText(UiErrors.safeMessage(ex));
            }
            updateActions();
        }, error->
        {
            if (token != generation || !scope.equals(scope()))return;
            busy = false;
            root.setDisable(false);
            status.setText(operation + " failed; draft retained: " + UiErrors.safeMessage(error));
            updateActions();
            commandFeedback.accept(new RunCommandResult(false, status.getText()));
        });
    }

    private String scope()
    {
        return database.get() + "|" + company.get();
    }
    private Draft draft()
    {
        return drafts.computeIfAbsent(scope() + "|" + selected.id(), ignored->new Draft());
    }
    private void markAccount()
    {
        if (!loading && selected != null)
        {
            draft().accountDirty = true;
            capture();
        }
    }
    private void capture()
    {
        if (loading || selected == null)
        {
            return;
        }
        Draft d = draft();
        d.nickname = nickname.getText();
        d.masked = masked.getText();
        d.active = active.isSelected();
        d.end = statementEnd.getValue();
        d.ending = statementEnding.getText();
        d.firstDate = firstDate.getValue();
        d.firstAmount = firstAmount.getText();
        d.reviewed = reviewed.isSelected();
        capabilitiesChanged.run();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return drafts.values().stream().anyMatch(d -> d.accountDirty || !d.references.isEmpty()
                || d.headerDirty || d.openingDirty || d.manual.values().stream().anyMatch(manual -> manual.dirty));
    }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        if (busy) return Set.of();
        int page = pages.getSelectionModel().getSelectedIndex();
        if (page == 0)
        {
            return selected == null ? Set.of(AppCommand.NEW_ACTIVE)
                    : AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
        }
        if (selected == null || !selected.active()) return Set.of();
        if (page == 1) return saveReference.isDisable() ? Set.of() : Set.of(AppCommand.SAVE_ACTIVE);
        return saveProgress.isDisable() ? Set.of(AppCommand.NEW_ACTIVE)
                : AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }

    @Override
    public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return Optional.of(pages.getSelectionModel().getSelectedIndex() == 0
                ? ApplicationPermission.COMPANY_ADMIN : ApplicationPermission.BOOKKEEPING_WRITE);
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        if (command != AppCommand.SAVE_ACTIVE) return AppPanel.super.commandLabel(command);
        int page = pages.getSelectionModel().getSelectedIndex();
        if (page == 0) return "Save account details";
        if (page == 1) return "Save banking reference";
        return selected != null && draft().openingDirty && opening != null && (!opening.confirmed() || draft().editOpening)
                ? "Confirm starting balance" : "Save progress";
    }

    @Override
    public String saveUnavailableReason()
    {
        if (selected == null) return "Configure or select a bank account first.";
        if (!selected.active()) return "This account is inactive; reactivate it in Accounts to edit banking or reconciliation.";
        if (pages.getSelectionModel().getSelectedIndex() == 1)
            return "Select an editable, uncleared bank journal item; use Journal for protected-entry corrections.";
        return "Completed reconciliation history is read-only; use New statement to continue.";
    }

    @Override
    public void onSave()
    {
        switch (pages.getSelectionModel().getSelectedIndex())
        {
            case 0:
                saveAccount();
                break;
            case 1:
                saveReference();
                break;
            default:
                if (draft().openingDirty && snapshot != null && opening != null && (!opening.confirmed() || draft().editOpening))
                {
                    confirmOpening();
                }
                else
                {
                    saveProgress();
                }
        }
    }

    @Override
    public void onNew()
    {
        if (pages.getSelectionModel().getSelectedIndex() == 0)
        {
            AppPanel panel = maintenance.get();
            if (panel instanceof BankingPanel settings)
            {
                settings.selectConfiguredAccount(selected == null ? -1 : selected.id(), true);
            }
            secondary("New bank account", panel);
        }
        else
        {
            newStatement();
        }
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = Objects.requireNonNull(listener);
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        commandFeedback = Objects.requireNonNull(listener);
    }

    private Button action(String text, String id, Runnable command, ApplicationPermission permission)
    {
        Button button = new Button(text);
        button.setId(id);
        button.setWrapText(true);
        button.setOnAction(event-> {try
        {
            command.run();
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
                                   });
        if (permission != null)
        {
            writeButtons.add(button);
            UiPermissionGate.gate(button, permission, text);
        }
        return button;
    }
    private BigDecimal money(String text)
    {
        if (text == null || text.isBlank())
        {
            throw new IllegalArgumentException("Enter the amount explicitly; enter 0.00 to confirm zero.");
        }
        BigDecimal value = format.parseMoney(text);
        if (value == null || value.stripTrailingZeros().scale() > 2)
        {
            throw new IllegalArgumentException("Enter a whole-cent amount; fractional cents are not rounded.");
        }
        return value;
    }
    private static Label label(String text)
    {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
    private static TextField field(String id)
    {
        TextField value = new TextField();
        value.setId(id);
        return value;
    }
    private static FlowPane flow(Node... nodes)
    {
        return new FlowPane(8, 7, nodes);
    }
    private static void add(GridPane grid, int row, String name, Node value)
    {
        grid.add(label(name), 0, row);
        grid.add(value, 1, row);
        GridPane.setHgrow(value, Priority.ALWAYS);
    }
    private static Node titled(String title, Node node)
    {
        VBox box = new VBox(6, label(title), node);
        box.setMinSize(0, 0);
        VBox.setVgrow(node, Priority.ALWAYS);
        return box;
    }
    private static <T> TableView<T> table(String id)
    {
        TableView<T> table = new TableView<>();
        table.setId(id);
        table.setMinSize(0, 0);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        return table;
    }
    private Node vertical(Node first, Node second, String key, double position)
    {
        Node bottom = second instanceof Control ? second : PanelViewport.section(second);
        SplitPane split;
        if (first instanceof TableView<?>)
        {
            split = new SplitPane(first, bottom);
            split.setOrientation(Orientation.VERTICAL);
            split.setMinSize(0, 0);
            CompanySplitPaneStateBinder.bind(split, key, position);
        }
        else
        {
            split = ResizablePanelHeader.split(first, bottom, key, position);
        }
        split.setId(key);
        return split;
    }
    private <T, V> void column(TableView<T> table, String title, String id, Function<T, V> value)
    {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(title.length() > 15 ? 190 : 135);
        column.setMinWidth(45);
        column.setCellValueFactory(data->new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        column.setCellFactory(ignored->new TableCell<>()
        {
            @Override protected void updateItem(V item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty ||
                        item == null ? "" : item instanceof BigDecimal amount ? format.formatMoney(amount) : item instanceof LocalDate date ?
                        format.formatDate(date) : item.toString());
            }
        });
        table.getColumns().add(column);
    }
    private record Loaded(Snapshot snapshot, OpeningView opening, List<BankItem> journal, List<SessionSummary> history,
                          BankingWorkspaceQuery.StatementHeader header) { }
    private static final class Draft
    {
        Long statement;
        Long candidate;
        Long session;
        Long entry;
        LocalDate end;
        LocalDate firstDate;
        String ending;
        String firstAmount;
        boolean reviewed;
        String nickname;
        String masked;
        boolean active;
        boolean accountDirty;
        boolean headerDirty;
        boolean openingDirty;
        boolean editOpening;
        final Map<Long, String> references = new HashMap<>();
        final Map<Long, ManualDraft> manual = new HashMap<>();
    }
    private static final class ManualDraft
    {
        LocalDate date;
        String description;
        String reference;
        String amount;
        boolean dirty;
    }

}
