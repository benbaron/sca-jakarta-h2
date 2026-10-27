package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/** Authoritative period-close, reopen, and factual-history workspace. */
public class PeriodCloseRunsPanel implements AppPanel
{
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final BorderPane root = new BorderPane();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final ComboBox<String> rangeKind = new ComboBox<>();
    private final ComboBox<ClosedPeriodPolicy> reopenPolicy = new ComboBox<>();
    private final CheckBox requireReason = new CheckBox("Require reopening reason");
    private final TextField actor = new TextField(DesktopActorIdentity.current());
    private final TextField reason = new TextField();
    private final TableView<PeriodCloseRangeView> ranges = new TableView<>();
    private final TableView<PeriodCloseEventView> history = new TableView<>();
    private final Label status = new Label();
    private final Button close = new Button("Save Closed Range");
    private final javafx.beans.property.SimpleBooleanProperty closing = new javafx.beans.property.SimpleBooleanProperty();
    private java.util.concurrent.CompletableFuture<PeriodCloseRangeView> closeCompletion;
    private final AccountingReadinessPane readiness;
    private final CloseReviewEvidencePane evidence;
    private final TabPane tabs;
    private Runnable capabilitiesChanged = () -> { };

    public PeriodCloseRunsPanel()
    {
        root.setPadding(new Insets(10));
        root.setMinSize(0, 0);

        Label title = new Label("Period Close");
        title.getStyleClass().add("panel-title");
        status.setWrapText(true);
        status.setId("periodCloseStatus");

        rangeKind.getItems().setAll("CALCULATED", "CUSTOM");
        rangeKind.setValue("CALCULATED");
        reopenPolicy.getItems().setAll(ClosedPeriodPolicy.values());
        applyPreferenceDefaults();
        actor.setPromptText("Authenticated audit actor");
        actor.setEditable(false);
        reason.setPromptText("Optional close/reopen reason");
        companyFormat.install(startDate);
        companyFormat.install(endDate);

        Button useActiveMonth = new Button("Use Active Period");
        useActiveMonth.setOnAction(event -> setCalculatedMonth());
        close.setId("periodCloseSaveClosedRange");
        close.setOnAction(event -> closeRange());
        Button reopen = new Button("Reopen Selected");
        reopen.setOnAction(event -> reopenSelected());
        close.disableProperty().bind(closing.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        close.setTooltip(new javafx.scene.control.Tooltip("Requires a matching recorded review, zero blockers and reasons for every warning. Revalidated atomically before closing."));
        startDate.disableProperty().bind(closing);
        endDate.disableProperty().bind(closing);
        rangeKind.disableProperty().bind(closing);
        reason.disableProperty().bind(closing);
        UiPermissionGate.gate(reopen, ApplicationPermission.BOOKKEEPING_WRITE, "Reopen an accounting period range");
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> reload());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Start date"), 0, 0);
        form.add(startDate, 1, 0);
        form.add(new Label("End date"), 2, 0);
        form.add(endDate, 3, 0);
        form.add(new Label("Range type"), 0, 1);
        form.add(rangeKind, 1, 1);
        form.add(new Label("Actor"), 2, 1);
        form.add(actor, 3, 1);
        form.add(new Label("Reopen policy"), 0, 2);
        form.add(reopenPolicy, 1, 2);
        form.add(requireReason, 2, 2, 2, 1);
        form.add(new Label("Reason"), 0, 3);
        form.add(reason, 1, 3, 3, 1);

        HBox actions = new HBox(8, useActiveMonth, close, reopen, refresh);
        actions.getStyleClass().add("panel-action-row");
        root.setTop(new VBox(8, title, form, actions, status, new Separator()));

        configureRangeTable();
        configureHistoryTable();

        VBox rangePane = new VBox(6, new Label("Close ranges"), ranges);
        VBox historyPane = new VBox(6, new Label("Factual close/reopen history"), history);
        rangePane.setPadding(new Insets(8));
        historyPane.setPadding(new Insets(8));
        VBox.setVgrow(ranges, Priority.ALWAYS);
        VBox.setVgrow(history, Priority.ALWAYS);

        SplitPane split = new SplitPane(rangePane, historyPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        split.setMinSize(0, 0);
        split.setId("periodCloseTablesSplit");
        CompanySplitPaneStateBinder.bind(split, "period-close-tables", 0.58);
        readiness = new AccountingReadinessPane(startDate, endDate,
                () -> UiServiceRegistry.coreAccountingPacket()::previewReadiness,
                () -> activeCompanyCode() + "|" + MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        evidence = new CloseReviewEvidencePane(startDate, endDate, UiServiceRegistry::closeReviewEvidence,
                () -> activeCompanyCode() + "|" + MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        tabs = new TabPane(new Tab("Close History", split), new Tab("Readiness", readiness), new Tab("Review Evidence", evidence.root()));
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> capabilitiesChanged.run());
        evidence.setCommandCapabilitiesChangedListener(() -> capabilitiesChanged.run());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setId("periodCloseTabs");
        root.setCenter(tabs);
        var weakReadiness = new java.lang.ref.WeakReference<>(readiness);
        var weakEvidence = new java.lang.ref.WeakReference<>(evidence);
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored ->
        {
            var pane = weakReadiness.get();
            if (pane != null)
            {
                pane.invalidate();
            }
            var editor = weakEvidence.get();
            if (editor != null) editor.invalidate(true);
        });
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(ignored ->
        {
            var pane = weakReadiness.get();
            if (pane != null)
            {
                pane.invalidate();
            }
            var editor = weakEvidence.get();
            if (editor != null) editor.invalidate(true);
        });

        setCalculatedMonth();
        reload();
    }

    @Override
    public void onPanelShown()
    {
        applyPreferenceDefaults();
        readiness.invalidate();
    }

    @Override
    public String title()
    {
        return "Period Close";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override public java.util.Set<AppCommand> commandCapabilities()
    {
        return tabs.getSelectionModel().getSelectedIndex() == 2 ? evidence.commandCapabilities() : java.util.Set.of();
    }

    @Override public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE && tabs.getSelectionModel().getSelectedIndex() == 2 ? "Save Review" : AppPanel.super.commandLabel(command);
    }

    @Override public void onSave()
    {
        evidence.onSave();
    }
    @Override public boolean hasUnsavedChanges()
    {
        return evidence.hasUnsavedChanges();
    }
    @Override public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener == null ? () -> { } : listener;
    }

    private void configureRangeTable()
    {
        ranges.setId("periodCloseRangeTable");
        ranges.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ranges.setPlaceholder(new Label("No close ranges exist for the active company."));

        TableColumn<PeriodCloseRangeView, String> start = column("Start", "start", 110,
                row -> companyFormat.formatDate(row.startDate()));
        TableColumn<PeriodCloseRangeView, String> end = column("End", "end", 110,
                row -> companyFormat.formatDate(row.endDate()));
        TableColumn<PeriodCloseRangeView, String> kind = column("Type", "kind", 110,
                PeriodCloseRangeView::rangeKind);
        TableColumn<PeriodCloseRangeView, String> rangeStatus = column("Status", "status", 110,
                PeriodCloseRangeView::status);
        TableColumn<PeriodCloseRangeView, String> closedBy = column("Closed By", "closedBy", 140,
                PeriodCloseRangeView::closedBy);
        TableColumn<PeriodCloseRangeView, String> closedAt = column("Closed At", "closedAt", 190,
                row -> companyFormat.formatDateTime(row.closedAt()));
        TableColumn<PeriodCloseRangeView, String> reopenedBy = column("Reopened By", "reopenedBy", 140,
                row -> blank(row.reopenedBy()));
        TableColumn<PeriodCloseRangeView, String> reopenedAt = column("Reopened At", "reopenedAt", 190,
                row -> companyFormat.formatDateTime(row.reopenedAt()));
        TableColumn<PeriodCloseRangeView, String> closeReason = column("Close Reason", "closeReason", 220,
                row -> blank(row.closeReason()));
        TableColumn<PeriodCloseRangeView, String> reopenReason = column("Reopen Reason", "reopenReason", 220,
                row -> blank(row.reopenReason()));

        ranges.getColumns().addAll(
                start, end, kind, rangeStatus, closedBy, closedAt,
                reopenedBy, reopenedAt, closeReason, reopenReason);
    }

    private void configureHistoryTable()
    {
        history.setId("periodCloseHistoryTable");
        history.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        history.setPlaceholder(new Label("No period-close history exists for the active company."));

        TableColumn<PeriodCloseEventView, String> eventAt = column("Event At", "eventAt", 190,
                row -> companyFormat.formatDateTime(row.eventAt()));
        TableColumn<PeriodCloseEventView, String> eventType = column("Event", "eventType", 110,
                PeriodCloseEventView::eventType);
        TableColumn<PeriodCloseEventView, String> range = column("Range ID", "rangeId", 260,
                row -> row.closeRangeId().toString());
        TableColumn<PeriodCloseEventView, String> eventActor = column("Actor", "actor", 150,
                PeriodCloseEventView::actor);
        TableColumn<PeriodCloseEventView, String> eventReason = column("Reason", "reason", 280,
                row -> blank(row.reason()));

        history.getColumns().addAll(eventAt, eventType, range, eventActor, eventReason);
    }

    private void setCalculatedMonth()
    {
        LocalDate active = ActivePeriodContext.get();
        if (active == null)
        {
            active = LocalDate.now();
        }
        int configuredStartDay = MainWindow.sharedSessionState().preferences().periodStartDayOfMonth();
        CalculatedRange range = calculatedPeriod(active, configuredStartDay);
        startDate.setValue(range.start());
        endDate.setValue(range.end());
        rangeKind.setValue("CALCULATED");
    }

    static CalculatedRange calculatedPeriod(LocalDate active, int configuredStartDay)
    {
        LocalDate start = ActivePeriodContext.periodStartFor(YearMonth.from(active), configuredStartDay);
        return new CalculatedRange(start, start.plusMonths(1).minusDays(1));
    }

    private void closeRange()
    {
        if (closing.get())
        {
            return;
        }
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        String company = activeCompanyCode(), kind = rangeKind.getValue(), recordedActor = actor.getText(), closeReason = reason.getText();
        var capturedService = service();
        closing.set(true);
        closeCompletion = new java.util.concurrent.CompletableFuture<>();
        status.setText("Revalidating recorded review and closing range...");
        UiAsync.run("period-close-close-range", () -> capturedService.closeRange(company, start, end, kind, recordedActor, closeReason),
                closed -> {
                    closing.set(false);
                    status.setText("Closed " + companyFormat.formatDate(closed.startDate()) + " through " + companyFormat.formatDate(closed.endDate())
                            + " for " + closed.companyCode() + ".");
                    reason.clear();
                    reload();
                    closeCompletion.complete(closed);
                },
                ex -> { closing.set(false); status.setText("Could not close range: " + UiErrors.safeMessage(ex)); closeCompletion.completeExceptionally(ex); });
    }

    private void reopenSelected()
    {
        PeriodCloseRangeView selected = ranges.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a closed range before reopening it.");
            return;
        }
        if (!selected.closed())
        {
            status.setText("The selected range is already reopened.");
            return;
        }

        status.setText("Reopening selected range...");
        UiAsync.run("period-close-reopen-range", () -> service().reopenRange(
                        selected.id(),
                        actor.getText(),
                        reason.getText(),
                        reopenPolicy.getValue(),
                        requireReason.isSelected()),
                reopened -> {
                    status.setText("Reopened " + companyFormat.formatDate(reopened.startDate()) + " through " + companyFormat.formatDate(reopened.endDate())
                            + " for " + reopened.companyCode() + ".");
                    reason.clear();
                    reload();
                },
                ex -> status.setText("Could not reopen range: " + UiErrors.safeMessage(ex)));
    }

    private void reload()
    {
        String company = activeCompanyCode();
        status.setText("Loading period close state for " + company + "...");
        UiAsync.run("period-close-load", () -> new PeriodCloseData(
                        service().listRanges(company),
                        service().listEvents(company)),
                data -> {
                    ranges.getItems().setAll(data.ranges());
                    history.getItems().setAll(data.events());
                    status.setText("Loaded " + data.ranges().size() + " close range(s) and "
                            + data.events().size() + " history event(s) for " + company + ".");
                },
                ex -> status.setText("Could not load period close state: " + UiErrors.safeMessage(ex)));
    }

    private void applyPreferenceDefaults()
    {
        var preferences = MainWindow.sharedSessionState().preferences();
        reopenPolicy.setValue(preferences.closedPeriodPolicy());
        requireReason.setSelected(preferences.requireReopenReason());
    }

    ClosedPeriodPolicy reopenPolicyForTests()
    {
        return reopenPolicy.getValue();
    }

    boolean requireReasonForTests()
    {
        return requireReason.isSelected();
    }

    String actorForTests()
    {
        return actor.getText();
    }

    CloseReviewEvidencePane reviewEvidenceForTests()
    {
        return evidence;
    }

    java.util.concurrent.CompletableFuture<PeriodCloseRangeView> closeCompletionForTests()
    {
        return closeCompletion;
    }

    private static PeriodCloseRangeService service()
    {
        return UiServiceRegistry.periodCloseRangeService();
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return company == null || company.isBlank()
                ? "DEFAULT"
                : company.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> TableColumn<T, String> column(
            String title,
            String key,
            double width,
            java.util.function.Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        column.setCellValueFactory(cell -> new SimpleStringProperty(blank(value.apply(cell.getValue()))));
        return column;
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }

    record CalculatedRange(LocalDate start, LocalDate end)
    {
    }

    private record PeriodCloseData(
            java.util.List<PeriodCloseRangeView> ranges,
            java.util.List<PeriodCloseEventView> events)
    {
    }
}
