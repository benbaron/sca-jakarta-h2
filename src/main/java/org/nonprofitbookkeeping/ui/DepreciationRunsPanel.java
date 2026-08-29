package org.nonprofitbookkeeping.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.DepreciationPeriodBatchService;
import org.nonprofitbookkeeping.service.DepreciationRunView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Accounting-period fixed-asset depreciation preview, execution, and history workspace. */
public class DepreciationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<DepreciationPeriodBatchService.Item> previewTable = new TableView<>();
    private final TableView<DepreciationRunView> runs = new TableView<>();
    private final TextField notes = new TextField();
    private final Label period = new Label();
    private final Label status = new Label();
    private final Button refresh = new Button("Preview Active Period");
    private final Button run = new Button("Run Eligible Depreciation");
    private final Button openReport = new Button("Open Depreciation Report");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final DepreciationPeriodBatchService batchService =
            new DepreciationPeriodBatchService(UiServiceRegistry.fixedAssets());

    private DepreciationPeriodBatchService.Preview currentPreview;

    public DepreciationRunsPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0.0, 0.0);
        Label title = new Label("Depreciation Runs");
        title.getStyleClass().add("panel-title");
        status.setWrapText(true);

        refresh.setId("depreciationPeriodPreviewButton");
        run.setId("runPeriodDepreciationButton");
        openReport.setId("openDepreciationReportButton");
        refresh.disableProperty().bind(busy);
        run.disableProperty().bind(busy);
        openReport.disableProperty().bind(busy);
        notes.disableProperty().bind(busy);
        notes.setPromptText("Optional note copied to each committed depreciation run");

        refresh.setOnAction(e -> reload(null));
        run.setOnAction(e -> runPeriodDepreciation());
        openReport.setOnAction(e -> openDepreciationReport());
        ActivePeriodContext.activeDateProperty().addListener((obs, oldValue, newValue) -> reload(null));

        HBox actions = new HBox(
                8,
                refresh,
                run,
                openReport,
                new Label("Notes"),
                notes);
        actions.getStyleClass().add("panel-action-row");
        HBox.setHgrow(notes, Priority.ALWAYS);

        root.setTop(new VBox(6, title, period, actions, status, new Separator()));
        configurePreviewTable();
        configureRunTable();
        VBox previewRegion = tableRegion("Accounting-Period Preview", previewTable);
        VBox runRegion = tableRegion("Completed Depreciation Runs", runs);
        SplitPane split = new SplitPane(previewRegion, runRegion);
        split.setId("depreciationRunsSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        split.setMinSize(0.0, 0.0);
        CompanySplitPaneStateBinder.bind(split, "depreciation-runs", 0.55);
        root.setCenter(split);
        reload(null);
    }

    @Override
    public void onPanelShown()
    {
        reload(null);
    }

    private void configurePreviewTable()
    {
        previewTable.setId("depreciationPeriodPreviewTable");
        previewTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        previewTable.setPlaceholder(new Label("No fixed assets are available for this accounting-period preview."));
        previewColumn("Asset", item -> item.assetName(), 180);
        previewColumn("Status", item -> item.status().name(), 100);
        previewColumn("Acquired", item -> companyFormat.formatDate(item.acquisitionDate()), 110);
        previewColumn("Book Value", item -> companyFormat.formatMoney(item.bookValue()), 120);
        previewColumn("Proposed", item -> moneyOrBlank(item.proposedAmount()), 120);
        previewColumn("Disposition", item -> item.disposition().name(), 130);
        previewColumn("Reason", DepreciationPeriodBatchService.Item::reason, 340);
    }

    private void configureRunTable()
    {
        runs.setId("depreciationCompletedRunsTable");
        runs.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        runs.setPlaceholder(new Label("No depreciation runs have been recorded."));
        runColumn("Run Date", r -> companyFormat.formatDate(r.runDate()), 110);
        runColumn("Asset", DepreciationRunView::assetName, 180);
        runColumn("Amount", r -> companyFormat.formatMoney(r.depreciationAmount()), 110);
        runColumn("Txn", r -> String.valueOf(r.transactionId()), 90);
        runColumn("Notes", DepreciationRunView::notes, 220);
    }

    private void previewColumn(
            String title,
            java.util.function.Function<DepreciationPeriodBatchService.Item, String> value,
            double width)
    {
        TableColumn<DepreciationPeriodBatchService.Item, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(blank(value.apply(row.getValue()))));
        configureColumn(column, width);
        previewTable.getColumns().add(column);
    }

    private void runColumn(
            String title,
            java.util.function.Function<DepreciationRunView, String> value,
            double width)
    {
        TableColumn<DepreciationRunView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(blank(value.apply(row.getValue()))));
        configureColumn(column, width);
        runs.getColumns().add(column);
    }

    private static void configureColumn(TableColumn<?, ?> column, double width)
    {
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
    }

    private static VBox tableRegion(String title, TableView<?> table)
    {
        VBox region = new VBox(6, new Label(title), table);
        region.setMinHeight(0.0);
        region.setMinWidth(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        return region;
    }

    private void reload(String operationOutcome)
    {
        CalculatedPeriod active = activePeriod();
        busy.set(true);
        currentPreview = null;
        previewTable.getItems().clear();
        period.setText("Active accounting period: "
                + companyFormat.formatDate(active.start()) + " through "
                + companyFormat.formatDate(active.end())
                + "; depreciation posting date " + companyFormat.formatDate(active.end()) + ".");
        status.setText(operationOutcome == null
                ? "Building depreciation preview from authoritative fixed-asset history..."
                : operationOutcome + " Refreshing authoritative depreciation data...");
        UiAsync.run("depreciation-period-preview",
                () -> new DepreciationPanelData(
                        batchService.preview(activeCompanyCode(), active.start(), active.end()),
                        UiServiceRegistry.fixedAssets().listDepreciationRuns(activeCompanyCode())),
                data -> {
                    currentPreview = data.preview();
                    previewTable.getItems().setAll(data.preview().items());
                    runs.getItems().setAll(data.runs());
                    busy.set(false);
                    updateRunAvailability();
                    String loaded = "Previewed " + data.preview().items().size() + " asset(s): "
                            + data.preview().eligibleCount() + " eligible, proposed total "
                            + companyFormat.formatMoney(data.preview().proposedTotal()) + ".";
                    status.setText(operationOutcome == null ? loaded : operationOutcome + " " + loaded);
                },
                ex -> {
                    busy.set(false);
                    updateRunAvailability();
                    String failure = "Could not build depreciation preview: " + UiErrors.safeMessage(ex);
                    status.setText(operationOutcome == null ? failure : operationOutcome + " " + failure);
                });
    }

    private void updateRunAvailability()
    {
        run.setDisable(busy.get() || currentPreview == null || currentPreview.eligibleCount() == 0);
        openReport.setDisable(busy.get() || currentPreview == null);
    }

    private void runPeriodDepreciation()
    {
        DepreciationPeriodBatchService.Preview preview = currentPreview;
        if (preview == null || preview.eligibleCount() == 0)
        {
            status.setText("Preview the active period first; there are no eligible depreciation runs to commit.");
            return;
        }
        if (!confirmBatch(preview))
        {
            status.setText("Accounting-period depreciation was cancelled; no runs were attempted.");
            return;
        }

        busy.set(true);
        updateRunAvailability();
        String requestedNotes = notes.getText();
        status.setText("Running independently atomic depreciation for "
                + preview.eligibleCount() + " eligible asset(s)...");
        UiAsync.run("period-depreciation-run",
                () -> batchService.execute(preview, requestedNotes),
                result -> {
                    String outcome = "Period depreciation finished: "
                            + result.committedCount() + " committed, "
                            + result.skippedCount() + " skipped, "
                            + result.failedCount() + " failed.";
                    if (result.failedCount() > 0)
                    {
                        outcome += " Successful asset runs remain committed; retrying the period skips them and reattempts only still-eligible assets.";
                    }
                    reload(outcome);
                },
                ex -> reload("Could not orchestrate period depreciation: " + UiErrors.safeMessage(ex)
                        + " Any already-committed per-asset run remains an authoritative completed fact."));
    }

    private boolean confirmBatch(DepreciationPeriodBatchService.Preview preview)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Accounting-Period Depreciation");
        confirmation.setHeaderText("Commit " + preview.eligibleCount()
                + " independently atomic depreciation run(s)?");
        confirmation.setContentText(
                "Period: " + companyFormat.formatDate(preview.periodStart()) + " through "
                        + companyFormat.formatDate(preview.periodEnd()) + "\n"
                        + "Posting date: " + companyFormat.formatDate(preview.postingDate()) + "\n"
                        + "Proposed total: " + companyFormat.formatMoney(preview.proposedTotal()) + "\n\n"
                        + "Each asset creates its own canonical transaction and depreciation-run record. "
                        + "If one asset fails, successful prior assets remain committed and the failure is reported for retry.");
        CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.DEPRECIATION_RUNS);
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void openDepreciationReport()
    {
        DepreciationPeriodBatchService.Preview preview = currentPreview;
        if (preview == null)
        {
            status.setText("Preview the active accounting period before opening its depreciation report.");
            return;
        }
        DateRangeContext.set(new DateRange(preview.periodStart(), preview.periodEnd()));
        DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.REPORT_LIBRARY,
                "report=fixed-asset-depreciation-history-schedule;start="
                        + preview.periodStart() + ";end=" + preview.periodEnd());
        status.setText("Opened Report Library for "
                + companyFormat.formatDate(preview.periodStart()) + " through "
                + companyFormat.formatDate(preview.periodEnd())
                + ". Select Fixed Asset Depreciation History & Schedule if it is not already selected.");
    }

    private static CalculatedPeriod activePeriod()
    {
        LocalDate active = ActivePeriodContext.get();
        if (active == null)
        {
            active = LocalDate.now();
        }
        int startDay = ApplicationSessionContext.sharedSessionState()
                .preferences().periodStartDayOfMonth();
        LocalDate start = ActivePeriodContext.periodStartFor(
                java.time.YearMonth.from(active), startDay);
        return new CalculatedPeriod(start, start.plusMonths(1).minusDays(1));
    }

    private static String activeCompanyCode()
    {
        String company = ApplicationSessionContext.sharedSessionState()
                .multiCompany().activeCompanyCode();
        return company == null || company.isBlank() ? "DEFAULT" : company.trim();
    }

    private String moneyOrBlank(BigDecimal value)
    {
        return value == null ? "" : companyFormat.formatMoney(value);
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }

    private record CalculatedPeriod(LocalDate start, LocalDate end)
    {
    }

    private record DepreciationPanelData(
            DepreciationPeriodBatchService.Preview preview,
            List<DepreciationRunView> runs)
    {
    }

    @Override
    public String title()
    {
        return "Depreciation Runs";
    }

    @Override
    public Node root()
    {
        return root;
    }
}
