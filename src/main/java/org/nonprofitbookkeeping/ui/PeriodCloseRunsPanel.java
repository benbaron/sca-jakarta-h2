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
import org.nonprofitbookkeeping.service.FiscalPeriodRange;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;
import org.nonprofitbookkeeping.service.PeriodCloseCoverage;

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
    private final ComboBox<YearMonth> periodSelector = new ComboBox<>();
    private final Label activePeriodSummary = new Label();
    private final ComboBox<String> rangeKind = new ComboBox<>();
    private final ComboBox<ClosedPeriodPolicy> reopenPolicy = new ComboBox<>();
    private final CheckBox requireReason = new CheckBox("Require reopening reason");
    private final TextField actor = new TextField(DesktopActorIdentity.current());
    private final TextField reason = new TextField();
    private final TableView<PeriodCloseRangeView> ranges = new TableView<>();
    private final TableView<PeriodCloseEventView> history = new TableView<>();
    private final Label status = new Label();
    private final Button close = new Button("Save Closed Range");
    private final Button reopen = new Button("Reopen Selected");
    private final java.util.function.Supplier<PeriodCloseRangeService> services;
    private long loadGeneration;
    private PeriodCloseNavigation.Request navigation;
    private java.util.concurrent.CompletableFuture<Void> loadCompletion;
    private java.util.concurrent.CompletableFuture<PeriodCloseRangeView> reopenCompletion;
    private final javafx.beans.property.SimpleBooleanProperty closing = new javafx.beans.property.SimpleBooleanProperty();
    private java.util.concurrent.CompletableFuture<PeriodCloseRangeView> closeCompletion;
    private final AccountingReadinessPane readiness;
    private final CloseReviewEvidencePane evidence;
    private final TabPane tabs;
    private ClosedPeriodPolicy lastDefaultPolicy;
    private boolean lastDefaultRequireReason;
    private Runnable capabilitiesChanged = () -> { };

    public PeriodCloseRunsPanel()
    {
        this(UiServiceRegistry::periodCloseRangeService);
    }

    PeriodCloseRunsPanel(java.util.function.Supplier<PeriodCloseRangeService> services)
    {
        this.services = java.util.Objects.requireNonNull(services);
        root.setPadding(new Insets(10));
        root.setMinHeight(0);

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
        reason.setId("periodCloseReason");
        reopenPolicy.setId("periodCloseReopenPolicy");
        requireReason.setId("periodCloseRequireReason");
        companyFormat.install(startDate);
        companyFormat.install(endDate);
        startDate.setId("periodCloseStartDate");
        endDate.setId("periodCloseEndDate");

        Button useActiveMonth = new Button("Use Active Period");
        useActiveMonth.setId("periodCloseUseActivePeriod");
        useActiveMonth.setOnAction(event -> setCalculatedMonth());
        close.setId("periodCloseSaveClosedRange");
        close.setOnAction(event -> closeRange());
        reopen.setId("periodCloseReopenSelected");
        reopen.setTooltip(new javafx.scene.control.Tooltip("Reopen the selected stored range using this policy and reason. Requires bookkeeping permission."));
        reopen.setOnAction(event -> reopenSelected());
        close.disableProperty().bind(closing.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        close.setTooltip(new javafx.scene.control.Tooltip("Requires a matching recorded review, zero blockers and reasons for every warning. Revalidated atomically before closing."));
        startDate.disableProperty().bind(closing);
        endDate.disableProperty().bind(closing);
        rangeKind.disableProperty().bind(closing);
        reason.disableProperty().bind(closing);
        reopen.disableProperty().bind(closing.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        reopenPolicy.disableProperty().bind(closing);
        requireReason.disableProperty().bind(closing);
        ranges.disableProperty().bind(closing);
        useActiveMonth.disableProperty().bind(closing);
        Button refresh = new Button("Refresh");
        refresh.setId("periodCloseRefresh");
        refresh.disableProperty().bind(closing);
        refresh.setOnAction(event -> reload());

        periodSelector.setId("periodClosePeriodSelector");
        periodSelector.setPromptText("Accounting month");
        Button setPeriod = new Button("Set Active Period");
        setPeriod.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        setPeriod.setId("periodCloseSetActivePeriod");
        setPeriod.setTooltip(new javafx.scene.control.Tooltip(
                "Set shared defaults for entry, depreciation, Dashboard and reports. Existing drafts stay unchanged; this does not open or close dates."));
        setPeriod.disableProperty().bind(closing.or(periodSelector.valueProperty().isNull()));
        periodSelector.disableProperty().bind(closing);
        setPeriod.setOnAction(event ->
        {
            ActivePeriodContext.select(periodSelector.getValue(),
                    MainWindow.sharedSessionState().preferences().periodStartDayOfMonth());
            refreshActivePeriod();
            reload();
        });
        activePeriodSummary.setId("periodCloseActivePeriodRange");
        activePeriodSummary.setWrapText(true);
        activePeriodSummary.setMinWidth(0);
        refreshActivePeriod();

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label periodLabel = new Label("Accounting period");
        periodLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        form.add(periodLabel, 0, 0);
        form.add(periodSelector, 1, 0);
        form.add(setPeriod, 2, 0);
        form.add(activePeriodSummary, 3, 0);
        form.add(new Label("Start date"), 0, 1);
        form.add(startDate, 1, 1);
        form.add(new Label("End date"), 2, 1);
        form.add(endDate, 3, 1);
        form.add(new Label("Range type"), 0, 2);
        form.add(rangeKind, 1, 2);
        form.add(new Label("Actor"), 2, 2);
        form.add(actor, 3, 2);
        form.add(new Label("Reopen policy"), 0, 3);
        form.add(reopenPolicy, 1, 3);
        form.add(requireReason, 2, 3, 2, 1);
        form.add(new Label("Reason"), 0, 4);
        form.add(reason, 1, 4, 3, 1);

        HBox actions = new HBox(8, useActiveMonth, close, reopen, refresh);
        actions.getStyleClass().add("panel-action-row");
        status.setWrapText(true);
        VBox header = new VBox(8, title, form, actions, status, new Separator());
        header.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.control.ScrollPane headerScroll = new javafx.scene.control.ScrollPane(header);
        headerScroll.setId("periodCloseHeaderScroll");
        headerScroll.setFitToWidth(true);
        headerScroll.setFitToHeight(true);
        headerScroll.setMinSize(0, 0);
        headerScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setTop(headerScroll);

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
        tabs = new TabPane(new Tab("Close History", split), new Tab("Readiness", readiness),
                new Tab("Review Evidence", evidence.root()),
                new Tab("Draft References", evidence.draftReferencesRoot()),
                new Tab("Recorded Reviews", evidence.recordedReviewsRoot()));
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

        var weakPanel = new java.lang.ref.WeakReference<>(this);
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored ->
        {
            var panel = weakPanel.get();
            if (panel != null) panel.scopeChanged();
        });
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(ignored ->
        {
            var panel = weakPanel.get();
            if (panel != null) panel.scopeChanged();
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
        refreshActivePeriod();
        readiness.invalidate();
        PeriodCloseNavigation.parse(DrillThroughCoordinator.consumeContext(AppPanelId.PERIOD_CLOSE_RUNS))
                .ifPresent(request -> navigation = request);
        reload();
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
        return tabs.getSelectionModel().getSelectedIndex() >= 2 ? evidence.commandCapabilities() : java.util.Set.of();
    }

    @Override public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE && tabs.getSelectionModel().getSelectedIndex() >= 2 ? "Save Review" : AppPanel.super.commandLabel(command);
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
        ranges.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
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

    private void refreshActivePeriod()
    {
        LocalDate selectedStart = ActivePeriodContext.get();
        periodSelector.getItems().setAll(ActivePeriodContext.periodChoicesFor(selectedStart));
        periodSelector.setValue(YearMonth.from(selectedStart));
        FiscalPeriodRange range = UiServiceRegistry.budgetPlan().fiscalRange(selectedStart);
        activePeriodSummary.setText(companyFormat.formatDate(range.periodStart())
                + " through " + companyFormat.formatDate(range.periodEnd()));
    }

    private void setCalculatedMonth()
    {
        FiscalPeriodRange range = UiServiceRegistry.budgetPlan().fiscalRange(ActivePeriodContext.get());
        startDate.setValue(range.periodStart());
        endDate.setValue(range.periodEnd());
        rangeKind.setValue("CALCULATED");
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
        String scope = scopeKey();
        closing.set(true);
        ++loadGeneration;
        var completion = new java.util.concurrent.CompletableFuture<PeriodCloseRangeView>();
        closeCompletion = completion;
        status.setText("Revalidating recorded review and closing range...");
        UiAsync.run("period-close-close-range", () -> capturedService.closeRange(company, start, end, kind, recordedActor, closeReason),
                closed -> {
                    closing.set(false);
                    if (scope.equals(scopeKey()))
                    {
                        reason.clear(); readiness.invalidate(); evidence.invalidate(false);
                        reload("Closed " + companyFormat.formatDate(closed.startDate()) + " through "
                                + companyFormat.formatDate(closed.endDate()) + " for " + closed.companyCode() + ".");
                    }
                    else reload();
                    completion.complete(closed);
                },
                ex -> {
                    closing.set(false);
                    if (scope.equals(scopeKey())) status.setText("Could not close range: " + UiErrors.safeMessage(ex));
                    else reload();
                    completion.completeExceptionally(ex);
                });
    }

    private void reopenSelected()
    {
        if (closing.get()) return;
        PeriodCloseRangeView selected = ranges.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.closed())
        {
            status.setText("Select a closed range before reopening it.");
            return;
        }
        String company = activeCompanyCode();
        if (!company.equals(selected.companyCode()))
        {
            status.setText("The selected range belongs to a different company. Refresh first.");
            return;
        }
        String scope = scopeKey(), recordedActor = actor.getText(), reopenReason = reason.getText();
        ClosedPeriodPolicy policy = reopenPolicy.getValue();
        boolean required = requireReason.isSelected();
        var capturedService = service();
        var completion = new java.util.concurrent.CompletableFuture<PeriodCloseRangeView>();
        reopenCompletion = completion;
        closing.set(true);
        ++loadGeneration;
        status.setText("Reopening selected range...");
        UiAsync.run("period-close-reopen-range",
                () -> capturedService.reopenRange(selected.id(), recordedActor, reopenReason, policy, required),
                reopened ->
                {
                    closing.set(false);
                    if (scope.equals(scopeKey()))
                    {
                        reason.clear();
                        readiness.invalidate();
                        evidence.invalidate(false);
                        reload("Reopened " + companyFormat.formatDate(reopened.startDate()) + " through "
                                + companyFormat.formatDate(reopened.endDate()) + " for " + reopened.companyCode() + ".");
                    }
                    else reload();
                    completion.complete(reopened);
                },
                ex ->
                {
                    closing.set(false);
                    if (scope.equals(scopeKey())) status.setText("Could not reopen range: " + UiErrors.safeMessage(ex));
                    else reload();
                    completion.completeExceptionally(ex);
                });
    }

    private void reload()
    {
        reload(null);
    }

    private void reload(String completedAction)
    {
        String company = activeCompanyCode(), scope = scopeKey();
        var capturedService = service();
        FiscalPeriodRange period = UiServiceRegistry.budgetPlan().fiscalRange(ActivePeriodContext.get());
        long generation = ++loadGeneration;
        var completion = new java.util.concurrent.CompletableFuture<Void>();
        loadCompletion = completion;
        status.setText("Loading period close state for " + company + "...");
        UiAsync.run("period-close-load", () -> new PeriodCloseData(
                        capturedService.listRanges(company), capturedService.listEvents(company)),
                data ->
                {
                    if (generation != loadGeneration || !scope.equals(scopeKey()))
                    {
                        completion.cancel(false);
                        return;
                    }
                    ranges.getItems().setAll(data.ranges());
                    history.getItems().setAll(data.events());
                    PeriodCloseCoverage coverage = PeriodCloseCoverage.of(period.periodStart(), period.periodEnd(), data.ranges());
                    activePeriodSummary.setText(companyFormat.formatDate(period.periodStart()) + " through "
                            + companyFormat.formatDate(period.periodEnd()) + " · " + coverage.label());
                    String navigationMessage = applyNavigation(data);
                    status.setText(completedAction == null
                            ? navigationMessage != null ? navigationMessage
                                : "Loaded " + data.ranges().size() + " close range(s) and " + data.events().size()
                                    + " history event(s) for " + company + "."
                            : completedAction);
                    completion.complete(null);
                },
                ex ->
                {
                    if (generation == loadGeneration && scope.equals(scopeKey()))
                    {
                        activePeriodSummary.setText(companyFormat.formatDate(period.periodStart()) + " through "
                                + companyFormat.formatDate(period.periodEnd()) + " · State unavailable");
                        status.setText("Could not load period close state: " + UiErrors.safeMessage(ex));
                    }
                    completion.completeExceptionally(ex);
                });
    }

    private String applyNavigation(PeriodCloseData data)
    {
        if (navigation == null) return null;
        var request = navigation;
        navigation = null;
        if (!request.company().equals(activeCompanyCode()) || !request.database().equals(PeriodCloseNavigation.database()))
        {
            return "The requested range belongs to another company or database. Select the intended scope first.";
        }
        var matches = data.ranges().stream()
                .filter(row -> request.rangeId() != null ? request.rangeId().equals(row.id())
                        : row.closed() && !row.startDate().isAfter(request.end()) && !row.endDate().isBefore(request.start()))
                .toList();
        if (!matches.isEmpty())
        {
            tabs.getSelectionModel().select(0);
            ranges.getSelectionModel().clearSelection();
            for (var row : matches) ranges.getSelectionModel().select(row);
            ranges.scrollTo(matches.get(0));
            history.getItems().setAll(data.events().stream()
                    .filter(event -> matches.stream().anyMatch(row -> row.id().equals(event.closeRangeId()))).toList());
            return "Viewing " + matches.size() + " affected close range(s).";
        }
        return request.rangeId() == null ? "No closed range overlaps the requested dates."
                : "The requested close range is no longer available in this scope.";
    }

    private void scopeChanged()
    {
        ++loadGeneration;
        navigation = null;
        ranges.getItems().clear();
        history.getItems().clear();
        activePeriodSummary.setText("Close state unavailable · Refresh");
        status.setText("Company/database changed. Refresh close state.");
    }

    private static String scopeKey()
    {
        return activeCompanyCode() + "|" + PeriodCloseNavigation.database();
    }

    private void applyPreferenceDefaults()
    {
        if (closing.get()) return;
        var preferences = MainWindow.sharedSessionState().preferences();
        if (lastDefaultPolicy == preferences.closedPeriodPolicy()
                && lastDefaultRequireReason == preferences.requireReopenReason()) return;
        lastDefaultPolicy = preferences.closedPeriodPolicy();
        lastDefaultRequireReason = preferences.requireReopenReason();
        reopenPolicy.setValue(lastDefaultPolicy);
        requireReason.setSelected(lastDefaultRequireReason);
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

    java.util.concurrent.CompletableFuture<Void> loadCompletionForTests() { return loadCompletion; }
    java.util.concurrent.CompletableFuture<PeriodCloseRangeView> reopenCompletionForTests() { return reopenCompletion; }

    private PeriodCloseRangeService service()
    {
        return services.get();
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

    private record PeriodCloseData(
            java.util.List<PeriodCloseRangeView> ranges,
            java.util.List<PeriodCloseEventView> events)
    {
    }
}
