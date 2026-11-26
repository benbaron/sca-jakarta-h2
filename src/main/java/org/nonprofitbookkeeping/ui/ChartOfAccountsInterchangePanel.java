package org.nonprofitbookkeeping.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.interchange.coa.*;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.ChartDeletionPreview;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Chart maintenance and its inline JSON proposal review share one workspace tab. */
public final class ChartOfAccountsInterchangePanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ChartOfAccountsPanel delegate;
    private final Button importJson = new Button("Import Chart JSON…");
    private final Button exportJson = new Button("Export Chart JSON…");
    private final Button newChart = new Button("New Chart");
    private final Button editChart = new Button("Edit Chart");
    private final Button deleteChart = new Button("Delete Draft Chart");
    private Predicate<ChartDeletionPreview> chartDeleteConfirmation = this::confirmChartDeletion;
    private final Button cloneChart = new Button("Clone Chart");
    private final Label nativeHint = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label interchangeStatus = new Label("JSON export uses the viewed chart.");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty chartLoading = new SimpleBooleanProperty(true);
    private final BooleanProperty working = new SimpleBooleanProperty(false);
    private final CoaJsonReviewPane review;
    private final NativeChartEditorPane nativeEditor;
    private final BiFunction<FileChooser, Window, File> chooser;
    private final BiFunction<FileChooser, Window, File> exportChooser;
    private boolean reviewing;
    private boolean chartEditing;
    private Runnable capabilitiesChanged = () ->
    {
    };
    private final javafx.beans.value.ChangeListener<Boolean> chartPermission = (o,a,b) -> changed();

    public ChartOfAccountsInterchangePanel()
    {
        this(new ChartOfAccountsPanel());
    }
    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate)
    {
        this(delegate,(fileChooser,owner) -> fileChooser.showOpenDialog(owner));
    }
    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate, BiFunction<FileChooser,Window,File> chooser)
    {
        this(delegate, chooser, (fileChooser, owner) -> fileChooser.showSaveDialog(owner));
    }
    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate, BiFunction<FileChooser,Window,File> chooser,
        BiFunction<FileChooser,Window,File> exportChooser)
    {
        this.delegate = Objects.requireNonNull(delegate);
        this.chooser = chooser;
        this.exportChooser = exportChooser;
        review = new CoaJsonReviewPane(new CoaJsonReviewPane.Operations()
        {
            @Override
            public CoaImportPreview preview(CoaImportRequest request, CoaJsonReviewPane.Scope scope)
            {
                return scoped(scope,jpa -> new ChartOfAccountsJsonService(jpa,scope::company).preview(request));
            }
            @Override
            public String sourceText(CoaImportPreview preview, CoaJsonReviewPane.Scope scope)
            {
                return scoped(scope,jpa -> new ChartOfAccountsJsonService(jpa,scope::company).sourceText(preview));
            }
            @Override
            public CoaImportResult commit(CoaImportPreview approved, CoaJsonReviewPane.Scope scope)
            {
                if (!scope.currentScope()) throw new IllegalStateException("Return to the original company/database before importing.");
                return UiServiceRegistry.coaJsonImport().commit(approved);
            }
        },this::chooseImportFile,this::showAccounts,CompanyUiFormat.activeCompany());
        nativeEditor = new NativeChartEditorPane(UiServiceRegistry::chartAdmin,this::chartSaved,this::showAccounts);
        nativeEditor.setCommandCapabilitiesChangedListener(this::changed);
        review.setCommandCapabilitiesChangedListener(this::changed);
        delegate.setCommandCapabilitiesChangedListener(this::changed);
        busy.addListener((o,a,b) -> changed());
        importJson.setId("coaImportJsonButton");
        exportJson.setId("coaExportJsonButton");
        importJson.disableProperty().bind(working.or(chartLoading).or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        exportJson.setTooltip(new Tooltip("Export saved definitions from View chart, including a draft, without activating it."));
        UiPermissionGate.gate(exportJson, ApplicationPermission.EXPORT, "Export the viewed Chart of Accounts to JSON");
        importJson.setOnAction(event -> requestImport());
        exportJson.setOnAction(event -> requestExport());
        newChart.setId("coaNewChart");
        editChart.setId("coaEditChart");
        cloneChart.setId("coaCloneChart");
        deleteChart.setId("coaDeleteDraftChart");
        deleteChart.setOnAction(event -> requestChartDeletion());
        newChart.setOnAction(event -> requestChart(NativeChartEditorPane.Mode.NEW));
        editChart.setOnAction(event -> requestChart(NativeChartEditorPane.Mode.EDIT));
        cloneChart.setOnAction(event -> requestChart(NativeChartEditorPane.Mode.CLONE));
        nativeHint.setWrapText(true);
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(new javafx.beans.value.WeakChangeListener<>(chartPermission));
        interchangeStatus.setId("coaInterchangeStatus");
        interchangeStatus.setWrapText(true);
        progress.setMaxSize(18,18);
        FlowPane toolbar = new FlowPane(8,6,newChart,editChart,cloneChart,deleteChart,importJson,exportJson,progress);
        toolbar.setPadding(new Insets(8));
        toolbar.setMinWidth(0);
        root.setMinSize(0,0);
        // This wrapper owns and swaps its center. Keep its persistent toolbar outside that center.
        ResizablePanelHeader.keepCompact(root);
        interchangeStatus.prefWidthProperty().bind(root.widthProperty().subtract(24));
        nativeHint.prefWidthProperty().bind(root.widthProperty().subtract(24));
        VBox header = new VBox(6,toolbar,nativeHint,interchangeStatus,new Separator());
        header.setMinHeight(Region.USE_PREF_SIZE);
        ScrollPane headerScroll = new ScrollPane(header);
        headerScroll.setId("coaChartToolbarScroll");
        headerScroll.setFitToWidth(true);
        headerScroll.setMinSize(0,0);
        headerScroll.maxHeightProperty().bind(root.heightProperty().multiply(.28));
        root.setTop(headerScroll);
        root.setCenter(delegate.root());
        changed();
    }

    private <T> T scoped(CoaJsonReviewPane.Scope scope, Function<Jpa,T> operation)
    {
        try (Jpa jpa = new Jpa(DatabaseLocationService.resolveDatabasePath(scope.database())))
        {
            return operation.apply(jpa);
        }
    }

    private void requestImport()
    {
        if (working.get() || delegate.loadingCharts() && !review.hasUnsavedChanges()
            || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return;
        if (!delegate.loadingCharts())
        {
            review.configureTargets(delegate.chartChoices(), delegate.selectedChart() == null ? null : delegate.selectedChart().id());
        }
        reviewing = true;
        chartEditing = false;
        root.setCenter(review);
        changed();
        if (!review.hasUnsavedChanges()) review.chooseSource();
    }

    private void showAccounts()
    {
        if (working.get()) return;
        reviewing = false;
        chartEditing = false;
        root.setCenter(delegate.root());
        delegate.onPanelShown();
        changed();
    }

    private void changed()
    {
        boolean inFlight = busy.get() || review.busy() || nativeEditor.busy() || delegate.deletingAccount();
        working.set(inFlight);
        chartLoading.set(delegate.loadingCharts() && !review.hasUnsavedChanges());
        boolean allowed = UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE);
        boolean accountDirty = delegate.hasUnsavedChanges();
        newChart.setDisable(inFlight || !allowed || accountDirty);
        cloneChart.setDisable(inFlight || !allowed || accountDirty);
        editChart.setDisable(inFlight || !allowed || delegate.loadingCharts() || delegate.selectedChart() == null && !nativeEditor.hasUnsavedChanges());
        exportJson.setDisable(inFlight || delegate.loadingCharts() || delegate.selectedChart() == null);
        deleteChart.setDisable(!canDeleteChart());
        nativeHint.setText(!allowed ? "New/Edit/Clone Chart requires BOOKKEEPING_WRITE (ACCOUNTANT/MANAGER/ADMIN). Account editing uses BOOKKEEPING_WRITE."
            : accountDirty ? "Save or cancel account changes before New/Clone/Delete Chart."
            : !reviewing && !chartEditing && nativeEditor.hasUnsavedChanges() ? "A chart draft is retained. Save or cancel it before Delete Draft Chart."
            : !reviewing && !chartEditing && delegate.selectedChart()!=null && delegate.selectedChart().status()!=org.nonprofitbookkeeping.model.ChartStatus.DRAFT
            ? "Delete Draft Chart requires an unused, non-current DRAFT."
            : "");
        nativeHint.setVisible(!nativeHint.getText().isBlank());
        nativeHint.setManaged(nativeHint.isVisible());
        progress.setVisible(inFlight);
        progress.setManaged(inFlight);
        delegate.root().setDisable(inFlight);
        capabilitiesChanged.run();
    }

    private void requestChart(NativeChartEditorPane.Mode mode)
    {
        if (working.get() || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return;
        if (delegate.hasUnsavedChanges() && mode != NativeChartEditorPane.Mode.EDIT)
        {
            interchangeStatus.setText("Save or Cancel Account Changes first; the account draft is retained.");
            return;
        }
        var selected = delegate.selectedChart();
        if (mode == NativeChartEditorPane.Mode.EDIT && selected == null && !nativeEditor.hasUnsavedChanges()) return;
        reviewing = false;
        chartEditing = true;
        root.setCenter(nativeEditor.root());
        nativeEditor.begin(mode,selected == null ? null : selected.id());
        changed();
    }

    private void chartSaved(org.nonprofitbookkeeping.service.ChartDetails chart)
    {
        reviewing = false;
        chartEditing = false;
        root.setCenter(delegate.root());
        delegate.focusChart(chart.id());
        interchangeStatus.setText("Saved chart (ID " + chart.id() + ").");
        changed();
    }

    private boolean canDeleteChart()
    {
        var chart = delegate.selectedChart();
        return !working.get() && !reviewing && !chartEditing && !delegate.loadingCharts()
        && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)
        && chart != null && chart.status() == org.nonprofitbookkeeping.model.ChartStatus.DRAFT && !chart.activeForCompany()
        && !delegate.hasUnsavedChanges() && !nativeEditor.hasUnsavedChanges();
    }

    private void requestChartDeletion()
    {
        if (!canDeleteChart()) return;
        var scope = CoaJsonReviewPane.Scope.current();
        var service = UiServiceRegistry.chartAdmin();
        long id = delegate.selectedChart().id();
        setBusy(true,"Checking draft chart references…");
        UiAsync.run("coa-chart-delete-preview",() -> service.previewChartDeletion(id),preview ->
        {
            if (!scope.currentScope())
            {
                setBusy(false,"Company/database changed; draft chart deletion cancelled.");
                return;
            }
            if (!preview.canDelete())
            {
                setBusy(false,"Cannot delete draft chart: "+String.join("; ",preview.blockers()));
                return;
            }
            if (!chartDeleteConfirmation.test(preview))
            {
                setBusy(false,"Draft chart deletion cancelled; saved definitions are retained.");
                return;
            }
            if (!scope.currentScope())
            {
                setBusy(false,"Company/database changed; draft chart deletion cancelled.");
                return;
            }
            setBusy(true,"Deleting draft chart '"+preview.name()+"'…");
            UiAsync.run("coa-chart-delete",() ->
            {
                service.deleteChart(id,preview.revision());
                return preview;
            },deleted ->
            {
                if (scope.currentScope()) delegate.afterChartDeletion();
                setBusy(false,"Deleted draft chart '"+deleted.name()+"' (ID "+id+").");
            },error -> setBusy(false,"Could not delete draft chart: "+UiErrors.safeMessage(error)));
        },error -> setBusy(false,"Could not inspect draft chart deletion: "+UiErrors.safeMessage(error)));
    }

    private boolean confirmChartDeletion(ChartDeletionPreview preview)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete draft Chart of Accounts");
        alert.setHeaderText("Delete '"+preview.name()+"' / "+preview.version()+" (ID "+preview.chartId()+") from "+preview.companyCode()+"?");
        alert.setContentText("This permanently removes "+preview.effects()+". Other charts, shared dictionaries and audit/history are retained. Pending imports targeting this chart require a new review.");
        if (ownerWindow()!=null) alert.initOwner(ownerWindow());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    void setChartDeleteConfirmationForTests(Predicate<ChartDeletionPreview> confirmation)
    {
        chartDeleteConfirmation = confirmation;
    }

    private void requestExport()
    {
        if (working.get() || delegate.loadingCharts() || delegate.selectedChart() == null
            || !UiPermissionGate.allows(ApplicationPermission.EXPORT)) return;
        var scope = CoaJsonReviewPane.Scope.current();
        var chart = delegate.selectedChart();
        Optional<Path> destination = chooseExportFile();
        if (destination.isEmpty()) return;
        if (!scope.currentScope())
        {
            interchangeStatus.setText("Company/database changed; Chart JSON export cancelled. Select the intended chart again.");
            return;
        }
        Path target = normalizeJsonPath(destination.get());
        if (Files.exists(target))
        {
            interchangeStatus.setText("Chart of Accounts JSON not exported: destination exists. Choose a new file name: " + target);
            return;
        }
        setBusy(true, "Exporting viewed chart " + chart.name() + " (ID " + chart.id() + ") to " + target + "…");
        UiAsync.run("coa-json-export",
            () -> scoped(scope, jpa -> new ChartOfAccountsJsonService(jpa,scope::company).exportChart(chart.id(),target)),
            result ->
        {
            setBusy(false, "Exported chart " + chart.name() + " (ID " + chart.id() + "), " + result.accountCount()
                + " account(s) to " + result.destination() + "; SHA-256 " + result.sha256() + ".");
        }, error -> setBusy(false, "Chart of Accounts JSON export failed: " + UiErrors.safeMessage(error)));
    }

    private Optional<Path> chooseImportFile()
    {
        Window owner = ownerWindow();
        if (owner == null)
        {
            interchangeStatus.setText("Chart of Accounts JSON import is unavailable until the window is shown.");
            return Optional.empty();
        }
        FileChooser chooser = jsonFileChooser("Import Chart of Accounts JSON");
        File selected = this.chooser.apply(chooser,owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath().toAbsolutePath().normalize());
    }

    private Optional<Path> chooseExportFile()
    {
        Window owner = ownerWindow();
        if (owner == null)
        {
            interchangeStatus.setText("Chart of Accounts JSON export is unavailable until the window is shown.");
            return Optional.empty();
        }
        FileChooser chooser = jsonFileChooser("Export Chart of Accounts JSON");
        chooser.setInitialFileName("chart-of-accounts.json");
        File selected = exportChooser.apply(chooser,owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath().toAbsolutePath().normalize());
    }

    private static FileChooser jsonFileChooser(String title)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Chart of Accounts JSON", "*.json"));
        return chooser;
    }

    private static Path normalizeJsonPath(Path path)
    {
        String raw = path.toString();
        return raw.toLowerCase().endsWith(".json") ? path : Path.of(raw + ".json");
    }

    static Map<String, String> parseMappings(String text)
    {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (text == null || text.isBlank())
        {
            return Map.of();
        }
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++)
        {
            String line = lines[index].trim();
            if (line.isEmpty())
            {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1)
            {
                throw new IllegalArgumentException(
                    "Mapping line " + (index + 1) + " must be sourceCode=targetCode.");
            }
            String source = line.substring(0, separator).trim();
            String target = line.substring(separator + 1).trim();
            if (mappings.putIfAbsent(source, target) != null)
            {
                throw new IllegalArgumentException("Source code is mapped more than once: " + source + ".");
            }
        }
        return Map.copyOf(mappings);
    }

    private void setBusy(boolean value, String message)
    {
        busy.set(value);
        interchangeStatus.setText(message);
    }

    private Window ownerWindow()
    {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private AppPanel active()
    {
        return chartEditing ? nativeEditor : reviewing ? review : delegate;
    }

    @Override
    public String title()
    {
        return delegate.title();
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        return working.get() ? Set.of() : active().commandCapabilities();
    }

    @Override
    public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return active().requiredPermission(command);
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        return active().commandLabel(command);
    }

    @Override
    public String saveUnavailableReason()
    {
        return active().saveUnavailableReason();
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        return commandCapabilities().contains(command) ? active().executeCommand(command)
        : new RunCommandResult(false,GlobalCommandRegistry.label(command) + " is not available in "
            + title() + ". " + saveUnavailableReason());
    }

    @Override
    public void onNew()
    {
        active().onNew();
    }

    @Override
    public void onSave()
    {
        active().onSave();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return active().commandResultMessage(command);
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return delegate.hasUnsavedChanges() || review.hasUnsavedChanges() || nativeEditor.hasUnsavedChanges();
    }

    @Override
    public void onPanelShown()
    {
        active().onPanelShown();
    }

    @Override
    public void onPanelShownFrom(AppPanelId origin, String title)
    {
        delegate.onPanelShownFrom(origin,title);
        if (reviewing) review.onPanelShown();
        if (chartEditing) nativeEditor.onPanelShown();
    }

    @Override
    public void onImportAddSuggestion(ImportAddSuggestion suggestion)
    {
        if (working.get()) return;
        showAccounts();
        delegate.onImportAddSuggestion(suggestion);
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener;
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        delegate.setCommandResultListener(listener);
        review.setCommandResultListener(listener);
        nativeEditor.setCommandResultListener(listener);
    }
    ChartOfAccountsPanel accountPanelForTests()
    {
        return delegate;
    }

    Button importJsonButtonForTests()
    {
        return importJson;
    }

    Button exportJsonButtonForTests()
    {
        return exportJson;
    }

    Label interchangeStatusForTests()
    {
        return interchangeStatus;
    }

    static Map<String,String> parseMappingsForTests(String text)
    {
        return parseMappings(text);
    }
}
