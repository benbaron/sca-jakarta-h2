package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.EventAccountingQueryService;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.ActivityOption;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.FundOption;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.LinkedSplitRow;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.RelatedBankRow;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.ReviewFact;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.Workspace;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read-only Event Accounting workspace over Activity-linked canonical Journal facts. */
public final class EventAccountingPanel implements AppPanel
{
    private static final String LINKED_TABLE_ID = "eventAccountingLinkedSplitsTable";
    private static final String BANK_TABLE_ID = "eventAccountingRelatedBankTable";

    private final EventAccountingQueryService service;
    private final CompanyUiFormat companyFormat;
    private final BorderPane root = new BorderPane();
    private final ComboBox<ActivityOption> activity = new ComboBox<>();
    private final ComboBox<FundChoice> fund = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker throughDate = new DatePicker();
    private final TableView<LinkedSplitRow> linkedTable = new TableView<>();
    private final TableView<RelatedBankRow> bankTable = new TableView<>();
    private final Label status = new Label("Event Accounting is ready.");
    private final Label activityIdentity = new Label("—");
    private final Label activityStatus = new Label("—");
    private final Label income = new Label("—");
    private final Label expenses = new Label("—");
    private final Label net = new Label("—");
    private final Label linkedTransactions = new Label("—");
    private final Label bankInflows = new Label("—");
    private final Label bankOutflows = new Label("—");
    private final VBox reviewFacts = new VBox(4);
    private final Button openJournal = new Button("Open Selected in Journal");
    private boolean applyingDefaultDates;
    private boolean dateScopeDetached;

    public EventAccountingPanel()
    {
        this(UiServiceRegistry.eventAccounting());
    }

    EventAccountingPanel(EventAccountingQueryService service)
    {
        this.service = Objects.requireNonNull(service, "service");
        this.companyFormat = CompanyUiFormat.activeCompany();
        build();
        loadSelectorsAndDefaultScope();
        ActivePeriodContext.activeDateProperty().addListener((observable, oldValue, newValue) -> {
            if (!dateScopeDetached)
            {
                applyDefaultDateScope(newValue);
                reloadWorkspace();
            }
        });
    }

    @Override
    public String title()
    {
        return "Event Accounting";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onPanelShown()
    {
        loadSelectorsPreservingSelection();
        if (!dateScopeDetached)
        {
            applyDefaultDateScope(ActivePeriodContext.get());
        }
        reloadWorkspace();
    }

    private void build()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Event Accounting");
        title.getStyleClass().add("panel-title");
        Label caption = new Label(
                "Read-only Activity/event/project accounting from canonical Journal splits. " +
                        "No posting, approval, reconciliation, or close state is created here.");
        caption.setWrapText(true);
        root.setTop(new VBox(4, title, caption));

        companyFormat.install(fromDate);
        companyFormat.install(throughDate);
        configureLinkedTable();
        configureBankTable();

        SplitPane details = new SplitPane(linkedRegion(), bankRegion());
        details.setId("eventAccountingDetailSplit");
        details.setOrientation(Orientation.VERTICAL);
        details.setDividerPositions(0.55);
        details.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(details, "event-accounting-detail", 0.55);

        SplitPane outer = new SplitPane(infoRegion(), details);
        outer.setId("eventAccountingOuterSplit");
        outer.setOrientation(Orientation.VERTICAL);
        outer.setDividerPositions(0.36);
        outer.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(outer, "event-accounting-outer", 0.36);
        root.setCenter(outer);
        root.setBottom(status);
    }

    private Node infoRegion()
    {
        activity.setId("eventAccountingActivity");
        activity.setPrefWidth(360);
        fund.setId("eventAccountingFund");
        fund.setPrefWidth(300);
        fromDate.setId("eventAccountingFromDate");
        throughDate.setId("eventAccountingThroughDate");

        Button refresh = new Button("Refresh");
        Button resetScope = new Button("Use Active Period Scope");
        refresh.setOnAction(event -> reloadWorkspace());
        resetScope.setOnAction(event -> {
            dateScopeDetached = false;
            applyDefaultDateScope(ActivePeriodContext.get());
            reloadWorkspace();
        });
        openJournal.setId("eventAccountingOpenJournalButton");
        openJournal.setDisable(true);
        openJournal.setOnAction(event -> openSelectedInJournal());

        activity.setOnAction(event -> reloadWorkspace());
        fund.setOnAction(event -> reloadWorkspace());
        fromDate.valueProperty().addListener((obs, oldValue, newValue) -> dateChanged());
        throughDate.valueProperty().addListener((obs, oldValue, newValue) -> dateChanged());

        FlowPane controls = new FlowPane(8, 6,
                new Label("Activity"), activity,
                new Label("From"), fromDate,
                new Label("Through"), throughDate,
                new Label("Fund"), fund,
                refresh, resetScope, openJournal);
        controls.setAlignment(Pos.CENTER_LEFT);

        GridPane summary = new GridPane();
        summary.setHgap(14);
        summary.setVgap(5);
        summary.addRow(0,
                new Label("Activity ID / code"), activityIdentity,
                new Label("Status"), activityStatus,
                new Label("Linked transactions"), linkedTransactions);
        summary.addRow(1,
                new Label("Income"), income,
                new Label("Expenses"), expenses,
                new Label("Net"), net);
        summary.addRow(2,
                new Label("Related bank inflows"), bankInflows,
                new Label("Related bank outflows"), bankOutflows);

        Label bankExplanation = new Label(
                "Related bank movement means a configured ASSET/BANK account split in a transaction that also contains the selected Activity. " +
                        "It is contextual evidence, not an assertion that the bank split itself is allocated to the Activity.");
        bankExplanation.setWrapText(true);
        bankExplanation.getStyleClass().add("muted-text");

        Label factsTitle = new Label("Closeout review facts");
        factsTitle.getStyleClass().add("section-title");
        VBox content = new VBox(8,
                controls,
                new Separator(),
                summary,
                bankExplanation,
                factsTitle,
                reviewFacts);
        content.setPadding(new Insets(6, 0, 6, 0));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinSize(0, 0);
        return scroll;
    }

    private Node linkedRegion()
    {
        Label heading = new Label("Activity-tagged Journal splits");
        heading.getStyleClass().add("section-title");
        Label help = new Label(
                "Each row is one canonical split explicitly tagged with the selected Activity. Double-click a row to open its transaction in Journal.");
        help.setWrapText(true);
        VBox box = new VBox(5, heading, help, linkedTable);
        box.setMinSize(0, 0);
        VBox.setVgrow(linkedTable, Priority.ALWAYS);
        return box;
    }

    private Node bankRegion()
    {
        Label heading = new Label("Related configured-bank movement");
        heading.getStyleClass().add("section-title");
        Label help = new Label(
                "These are configured bank-account splits from the same canonical transactions. Inflow/outflow follows the current debit-normal ASSET/BANK authority.");
        help.setWrapText(true);
        VBox box = new VBox(5, heading, help, bankTable);
        box.setMinSize(0, 0);
        VBox.setVgrow(bankTable, Priority.ALWAYS);
        return box;
    }

    private void configureLinkedTable()
    {
        linkedTable.setId(LINKED_TABLE_ID);
        linkedTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        linkedTable.setPlaceholder(new Label("No Activity-tagged Journal splits match the selected scope."));
        linkedTable.getColumns().add(textColumn("Date", 110, row -> companyFormat.formatDate(row.transactionDate())));
        linkedTable.getColumns().add(textColumn("Transaction", 100, row -> Long.toString(row.transactionId())));
        linkedTable.getColumns().add(textColumn("Payee", 180, LinkedSplitRow::payee));
        linkedTable.getColumns().add(textColumn("Memo", 260, LinkedSplitRow::memo));
        linkedTable.getColumns().add(textColumn("Account", 240,
                row -> codeAndName(row.accountCode(), row.accountName())));
        linkedTable.getColumns().add(textColumn("Type", 90, row -> row.accountType().name()));
        linkedTable.getColumns().add(textColumn("Fund", 190,
                row -> codeAndName(row.fundCode(), row.fundName())));
        linkedTable.getColumns().add(textColumn("Debit", 120, row -> moneyOrBlank(row.debit())));
        linkedTable.getColumns().add(textColumn("Credit", 120, row -> moneyOrBlank(row.credit())));
        linkedTable.setRowFactory(table -> journalDrillRow(LinkedSplitRow::transactionId));
        linkedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null)
            {
                bankTable.getSelectionModel().clearSelection();
            }
            updateJournalActionState();
        });
    }

    private void configureBankTable()
    {
        bankTable.setId(BANK_TABLE_ID);
        bankTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bankTable.setPlaceholder(new Label("No related configured-bank movement matches the selected scope."));
        bankTable.getColumns().add(textColumn("Date", 110, row -> companyFormat.formatDate(row.transactionDate())));
        bankTable.getColumns().add(textColumn("Transaction", 100, row -> Long.toString(row.transactionId())));
        bankTable.getColumns().add(textColumn("Configured Account", 210, RelatedBankRow::configuredBankAccountName));
        bankTable.getColumns().add(textColumn("Ledger Account", 230,
                row -> codeAndName(row.accountCode(), row.accountName())));
        bankTable.getColumns().add(textColumn("Payee", 180, RelatedBankRow::payee));
        bankTable.getColumns().add(textColumn("Memo", 260, RelatedBankRow::memo));
        bankTable.getColumns().add(textColumn("Fund", 190,
                row -> codeAndName(row.fundCode(), row.fundName())));
        bankTable.getColumns().add(textColumn("Inflow", 120, row -> moneyOrBlank(row.inflow())));
        bankTable.getColumns().add(textColumn("Outflow", 120, row -> moneyOrBlank(row.outflow())));
        bankTable.getColumns().add(textColumn("Cleared", 90, row -> row.cleared() ? "Cleared" : "Uncleared"));
        bankTable.getColumns().add(textColumn("Cleared On", 110, row -> companyFormat.formatDate(row.clearedOn())));
        bankTable.setRowFactory(table -> journalDrillRow(RelatedBankRow::transactionId));
        bankTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null)
            {
                linkedTable.getSelectionModel().clearSelection();
            }
            updateJournalActionState();
        });
    }

    private <T> TableRow<T> journalDrillRow(java.util.function.Function<T, Long> transactionId)
    {
        TableRow<T> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && !row.isEmpty())
            {
                openTransaction(transactionId.apply(row.getItem()));
            }
        });
        return row;
    }

    private void loadSelectorsAndDefaultScope()
    {
        loadSelectorsPreservingSelection();
        applyDefaultDateScope(ActivePeriodContext.get());
        reloadWorkspace();
    }

    private void loadSelectorsPreservingSelection()
    {
        Long selectedActivityId = activity.getValue() == null ? null : activity.getValue().id();
        Long selectedFundId = selectedFundId();
        try
        {
            List<ActivityOption> activities = service.listActivities();
            activity.getItems().setAll(activities);
            activities.stream()
                    .filter(option -> Objects.equals(option.id(), selectedActivityId))
                    .findFirst()
                    .ifPresentOrElse(
                            activity.getSelectionModel()::select,
                            () -> {
                                if (!activities.isEmpty())
                                {
                                    activity.getSelectionModel().selectFirst();
                                }
                            });

            List<FundChoice> funds = new java.util.ArrayList<>();
            funds.add(new FundChoice(null, "All funds"));
            for (FundOption option : service.listFunds())
            {
                funds.add(new FundChoice(option.id(), option.toString()));
            }
            fund.getItems().setAll(funds);
            funds.stream()
                    .filter(option -> Objects.equals(option.id(), selectedFundId))
                    .findFirst()
                    .ifPresentOrElse(
                            fund.getSelectionModel()::select,
                            fund.getSelectionModel()::selectFirst);
        }
        catch (RuntimeException ex)
        {
            activity.getItems().clear();
            fund.getItems().setAll(new FundChoice(null, "All funds"));
            fund.getSelectionModel().selectFirst();
            clearWorkspace("Could not load Event Accounting selectors: " + UiErrors.safeMessage(ex));
        }
    }

    private void applyDefaultDateScope(LocalDate selectedPeriodStart)
    {
        if (selectedPeriodStart == null)
        {
            return;
        }
        try
        {
            FiscalPeriodRange range = service.fiscalRange(selectedPeriodStart);
            applyingDefaultDates = true;
            try
            {
                fromDate.setValue(range.fiscalYearStart());
                throughDate.setValue(range.periodEnd());
            }
            finally
            {
                applyingDefaultDates = false;
            }
            dateScopeDetached = false;
        }
        catch (RuntimeException ex)
        {
            clearWorkspace("Could not derive active-period Event Accounting scope: " + UiErrors.safeMessage(ex));
        }
    }

    private void dateChanged()
    {
        if (!applyingDefaultDates)
        {
            dateScopeDetached = true;
        }
    }

    private void reloadWorkspace()
    {
        ActivityOption selectedActivity = activity.getValue();
        LocalDate start = fromDate.getValue();
        LocalDate end = throughDate.getValue();
        if (selectedActivity == null)
        {
            clearWorkspace("Select an Activity to review Event Accounting.");
            return;
        }
        if (start == null || end == null)
        {
            clearWorkspace("Choose both From and Through dates.");
            return;
        }

        try
        {
            Workspace workspace = service.workspace(selectedActivity.id(), start, end, selectedFundId());
            apply(workspace);
            status.setText("Loaded Event Accounting for " + workspace.activity().code()
                    + " from " + companyFormat.formatDate(workspace.start())
                    + " through " + companyFormat.formatDate(workspace.end()) + ".");
        }
        catch (RuntimeException ex)
        {
            clearWorkspace("Could not load Event Accounting: " + UiErrors.safeMessage(ex));
        }
    }

    private void apply(Workspace workspace)
    {
        activityIdentity.setText(workspace.activity().id() + " / " + workspace.activity().code()
                + " — " + workspace.activity().name());
        activityStatus.setText(workspace.activity().active() ? "Active" : "Inactive");
        income.setText(companyFormat.formatMoney(workspace.summary().income()));
        expenses.setText(companyFormat.formatMoney(workspace.summary().expenses()));
        net.setText(companyFormat.formatMoney(workspace.summary().net()));
        linkedTransactions.setText(Integer.toString(workspace.summary().linkedTransactionCount()));
        bankInflows.setText(companyFormat.formatMoney(workspace.summary().relatedBankInflows()));
        bankOutflows.setText(companyFormat.formatMoney(workspace.summary().relatedBankOutflows()));
        linkedTable.getItems().setAll(workspace.linkedRows());
        bankTable.getItems().setAll(workspace.relatedBankRows());
        renderReviewFacts(workspace.reviewFacts());
        updateJournalActionState();
    }

    private void renderReviewFacts(List<ReviewFact> facts)
    {
        reviewFacts.getChildren().clear();
        for (ReviewFact fact : facts)
        {
            Label label = new Label(fact.label() + ": " + fact.status() + " — " + fact.detail());
            label.setWrapText(true);
            reviewFacts.getChildren().add(label);
        }
    }

    private void clearWorkspace(String message)
    {
        activityIdentity.setText("—");
        activityStatus.setText("—");
        income.setText("—");
        expenses.setText("—");
        net.setText("—");
        linkedTransactions.setText("—");
        bankInflows.setText("—");
        bankOutflows.setText("—");
        linkedTable.getItems().clear();
        bankTable.getItems().clear();
        reviewFacts.getChildren().clear();
        openJournal.setDisable(true);
        status.setText(message);
    }

    private void updateJournalActionState()
    {
        openJournal.setDisable(selectedTransactionId() == null);
    }

    private void openSelectedInJournal()
    {
        Long transactionId = selectedTransactionId();
        if (transactionId == null)
        {
            status.setText("Select an Activity-tagged or related bank row before opening Journal.");
            return;
        }
        openTransaction(transactionId);
    }

    private void openTransaction(Long transactionId)
    {
        if (transactionId == null)
        {
            return;
        }
        DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.JOURNAL_PANE,
                "Txn #" + transactionId + " from Event Accounting");
    }

    private Long selectedTransactionId()
    {
        LinkedSplitRow linked = linkedTable.getSelectionModel().getSelectedItem();
        if (linked != null)
        {
            return linked.transactionId();
        }
        RelatedBankRow bank = bankTable.getSelectionModel().getSelectedItem();
        return bank == null ? null : bank.transactionId();
    }

    private Long selectedFundId()
    {
        FundChoice selected = fund.getValue();
        return selected == null ? null : selected.id();
    }

    private String moneyOrBlank(java.math.BigDecimal value)
    {
        return value == null || value.signum() == 0 ? "" : companyFormat.formatMoney(value);
    }

    private static String codeAndName(String code, String name)
    {
        String left = code == null ? "" : code;
        String right = name == null ? "" : name;
        return left.isBlank() ? right : right.isBlank() ? left : left + " — " + right;
    }

    private static <T> TableColumn<T, String> textColumn(
            String title,
            double width,
            java.util.function.Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(value.apply(cell.getValue()), "")));
        return column;
    }

    private record FundChoice(Long id, String label)
    {
        @Override
        public String toString()
        {
            return label;
        }
    }
}
