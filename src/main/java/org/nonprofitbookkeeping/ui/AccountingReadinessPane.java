package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AccountingReadiness;
import org.nonprofitbookkeeping.service.ApplicationPermission;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Read-only preview over the packet authority, embedded in the canonical Period Close workspace. */
final class AccountingReadinessPane extends BorderPane
{
    @FunctionalInterface
    interface Capture
    {
        AccountingReadiness preview(LocalDate start, LocalDate end) throws Exception;
    }

    private final DatePicker start;
    private final DatePicker end;
    private final Supplier<Capture> capture;
    private final Supplier<String> scopeKey;
    private final TableView<AccountingReadiness.Finding> findings = new TableView<>();
    private final Label status = new Label("Preview the selected company and dates. Later edits require another preview.");
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    private CompletableFuture<AccountingReadiness> completion;
    private long generation;

    AccountingReadinessPane(DatePicker start, DatePicker end, Supplier<Capture> capture, Supplier<String> scopeKey)
    {
        this.start = start;
        this.end = end;
        this.capture = capture;
        this.scopeKey = scopeKey;
        setMinSize(0, 0);
        setId("periodCloseReadiness");
        Button preview = new Button("Preview Readiness");
        preview.setId("periodClosePreviewReadiness");
        preview.disableProperty().bind(busy.or(UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT)));
        preview.setTooltip(new javafx.scene.control.Tooltip("Capture automated checks for all Funds/Events. Requires EXPORT permission."));
        preview.setOnAction(event -> preview());
        Button open = new Button("Open Selected Finding");
        open.setId("periodCloseOpenFinding");
        open.disableProperty().bind(findings.getSelectionModel().selectedItemProperty().isNull()
                .or(UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT)).or(busy));
        open.setOnAction(event -> openSelected());
        Label explanation = new Label("Automated observations only. External evidence is not attested. This preview does not authorize or certify period close.");
        explanation.setWrapText(true);
        status.setWrapText(true);
        status.setId("periodCloseReadinessStatus");
        VBox controls = new VBox(8, new HBox(8, preview, open), explanation, status);
        controls.setMinSize(0, 0);
        findings.setId("periodCloseReadinessFindings");
        findings.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        findings.setPlaceholder(new Label("No current preview. Run Preview Readiness to inspect automated findings."));
        findings.getColumns().addAll(column("Severity", "severity", 100, row -> row.severity().name()),
                column("Area", "area", 150, AccountingReadiness.Finding::area),
                column("Reference", "reference", 170, AccountingReadiness.Finding::reference),
                column("Finding", "detail", 460, AccountingReadiness.Finding::detail),
                column("Review in", "destination", 160, row -> destinationLabel(row.destination())));
        SplitPane split = new SplitPane(controls, findings);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.25);
        split.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(split, "period-close-readiness", 0.25);
        setCenter(split);
        start.valueProperty().addListener((observable, oldValue, newValue) -> invalidate());
        end.valueProperty().addListener((observable, oldValue, newValue) -> invalidate());
        UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT).addListener(
                new javafx.beans.value.WeakChangeListener<Boolean>(permissionListener));
    }

    private final javafx.beans.value.ChangeListener<Boolean> permissionListener = (observable, oldValue, denied) ->
    {
        if (denied)
        {
            invalidate();
        }
    };

    void invalidate()
    {
        if (!javafx.application.Platform.isFxApplicationThread())
        {
            javafx.application.Platform.runLater(this::invalidate);
            return;
        }
        generation++;
        findings.getItems().clear();
        status.setText("Scope or access changed. Preview again for the current company and dates.");
    }

    private void preview()
    {
        if (busy.get() || !UiPermissionGate.allows(ApplicationPermission.EXPORT))
        {
            return;
        }
        invalidate();
        long request = generation;
        LocalDate from = start.getValue(), through = end.getValue();
        String scope = scopeKey.get();
        completion = new CompletableFuture<>();
        Capture operation;
        try
        {
            operation = capture.get();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not preview readiness: " + UiErrors.safeMessage(ex));
            completion.completeExceptionally(ex);
            return;
        }
        busy.set(true);
        status.setText("Capturing readiness for all Funds and Events…");
        UiAsync.run("period-close-readiness", () ->
        {
            try
            {
                return operation.preview(from, through);
            }
            catch (Exception ex)
            {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, result ->
        {
            busy.set(false);
            if (generation != request || !Objects.equals(scope, scopeKey.get())
                    || !UiPermissionGate.allows(ApplicationPermission.EXPORT)
                    || !Objects.equals(from, result.start()) || !Objects.equals(through, result.end()))
            {
                invalidate();
                completion.completeExceptionally(new java.util.concurrent.CancellationException("Preview scope changed"));
                return;
            }
            findings.getItems().setAll(result.findings());
            var format = CompanyUiFormat.activeCompany();
            status.setText(result.companyCode() + " | " + format.formatDate(result.start()) + " through " + format.formatDate(result.end())
                    + " | " + result.automatedStatus() + ": " + result.blockers() + " blocker(s), " + result.warnings()
                    + " warning(s). Content revision: " + result.dataRevision() + ". Captured " + format.formatDateTime(result.capturedAt())
                    + ". Preview only; external evidence not attested.");
            completion.complete(result);
        }, error ->
        {
            busy.set(false);
            invalidate();
            status.setText("Could not preview readiness: " + UiErrors.safeMessage(error));
            completion.completeExceptionally(error);
        });
    }

    private void openSelected()
    {
        var selected = findings.getSelectionModel().getSelectedItem();
        if (selected != null)
        {
            DrillThroughCoordinator.openPanelWithContext(destination(selected.destination()),
                    "Readiness review: " + selected.area() + " " + selected.reference());
        }
    }

    static AppPanelId destination(AccountingReadiness.Destination target)
    {
        return switch (target)
        {
            case JOURNAL -> AppPanelId.JOURNAL_PANE;
            case BANK_CONFIGURATION -> AppPanelId.BANKING;
            case RECONCILIATION -> AppPanelId.RECONCILIATION_RUNS;
            case ASSETS -> AppPanelId.ASSETS_REGISTER;
            case DEPRECIATION -> AppPanelId.DEPRECIATION_RUNS;
            case INVENTORY -> AppPanelId.INVENTORY;
            case CUSTODY -> AppPanelId.PROPERTY_CUSTODY;
            case BUDGET -> AppPanelId.BUDGET_EDITOR;
            case TRANSFERS -> AppPanelId.FUND_TRANSFERS;
            case OPENING_CONVERSION -> AppPanelId.OPENING_CONVERSION;
            case CHART -> AppPanelId.CHART_OF_ACCOUNTS;
            case REPORTS -> AppPanelId.REPORT_LIBRARY;
        };
    }

    private static String destinationLabel(AccountingReadiness.Destination target)
    {
        return switch (target)
        {
            case JOURNAL -> "Journal";
            case BANK_CONFIGURATION -> "Banking";
            case RECONCILIATION -> "Reconciliation";
            case ASSETS -> "Asset Register";
            case DEPRECIATION -> "Depreciation Runs";
            case INVENTORY -> "Inventory";
            case CUSTODY -> "Property Custody";
            case BUDGET -> "Budget Editor";
            case TRANSFERS -> "Fund Transfers";
            case OPENING_CONVERSION -> "Opening Conversion";
            case CHART -> "Chart of Accounts";
            case REPORTS -> "Report Library";
        };
    }

    CompletableFuture<AccountingReadiness> completion()
    {
        return completion;
    }

    private static TableColumn<AccountingReadiness.Finding, String> column(String title, String key,
            double width, Function<AccountingReadiness.Finding, String> value)
    {
        var column = new TableColumn<AccountingReadiness.Finding, String>(title);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }
}
